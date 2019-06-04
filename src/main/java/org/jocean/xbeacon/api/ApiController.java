package org.jocean.xbeacon.api;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Formatter;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

import javax.inject.Inject;
import javax.inject.Named;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;

import org.jocean.http.endpoint.internal.DefaultEndpointSet;
import org.jocean.idiom.BeanFinder;
import org.jocean.idiom.ExceptionUtils;
import org.jocean.idiom.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Controller;
import org.yaml.snakeyaml.Yaml;

import com.alibaba.acm.shaded.com.google.common.collect.Lists;
import com.alibaba.edas.acm.ConfigService;
import com.alibaba.fastjson.annotation.JSONField;
import com.google.common.base.Charsets;
import com.google.common.base.Strings;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;

import rx.Observable;

@Controller
@Scope("singleton")
public class ApiController {

    private static final ServiceInfo[] EMPTY_SRVINFOS = new ServiceInfo[0];

    private static final String[] EMPTY_STRS = new String[0];

    final static class NameValue {
        @JSONField(name = "name")
        public String getName() {
            return _name;
        }

        @JSONField(name = "name")
        public void setName(final String name) {
            this._name = name;
        }

        @JSONField(name = "value")
        public String getValue() {
            return _value;
        }

        @JSONField(name = "value")
        public void setValue(final String value) {
            this._value = value;
        }

        String _name;
        String _value;
    }

    private static final Logger LOG = LoggerFactory.getLogger(ApiController.class);

    @Inject
    @Named("services")
    List<ServiceInfo> _services;

    @Value("${ignores}")
    public void setIgnore(final String ignores) {
        this._ignores = Arrays.asList(ignores.split(","));
    }

    private List<String> _ignores = Collections.emptyList();

    Map<String, List<String>> _host2svrs = new ConcurrentHashMap<>();

    @Value("${acm.endpoint}")
    String _acmEndpoint;

    @Value("${acm.namespace}")
    String _acmNamespace;

    @Value("${ecs.rolename}")
    String _ecsRolename;

    @Value("${ver.dataid}")
    String _verDataId;

    @Value("${ver.group}")
    String _verGroup;

    @Path("/app-status/version")
    @Produces(MediaType.APPLICATION_JSON)
    public Object getVersion(@QueryParam("service") final String service) throws Exception {
        // 从控制台命名空间管理中拷贝对应值
        final Properties props = new Properties();
        props.put("endpoint", _acmEndpoint);
        props.put("namespace", _acmNamespace);
        // 通过 ECS 实例 RAM 角色访问 ACM
        props.put("ramRoleName", _ecsRolename);

        // 如果是加密配置，则添加下面两行进行自动解密
        //props.put("openKMSFilter", true);
        //props.put("regionId", "$regionId");

        ConfigService.init(props);

        final String content = ConfigService.getConfig(_verDataId, _verGroup, 6000);
        try (final InputStream is = new ByteArrayInputStream(content.getBytes(Charsets.UTF_8))) {
            final Map<String, String> srv2ver = asStringStringMap(null, (Map<Object, Object>)new Yaml().loadAs(is, Map.class));
            return new String[]{srv2ver.get(service)};
        } catch (final IOException e) {
            LOG.warn("exception when loadYaml from {}, detail: {}", content, ExceptionUtils.exception2detail(e));
            return new String[]{"-1"};
        }
    }

    private Map<String, String> asStringStringMap(final String prefix, final Map<Object, Object> map) {
        if (null == map) {
            return null;
        }
        final Map<String, String> ssmap = Maps.newHashMap();
        for(final Map.Entry<Object, Object> entry : map.entrySet()) {
            final String key = withPrefix(prefix, entry.getKey().toString());
            if (entry.getValue() instanceof Map) {
                @SuppressWarnings("unchecked")
                final Map<Object, Object> orgmap = (Map<Object, Object>)entry.getValue();
                ssmap.putAll(asStringStringMap(key, orgmap));
            } else {
                ssmap.put(key, entry.getValue().toString());
            }
        }
        return ssmap;
    }

    private String withPrefix(final String prefix, final String key) {
        return null != prefix ? prefix + "." + key : key;
    }

	@Path("/app-status/services")
	public String listServices(@QueryParam("hosts") final String hostsAsString) {

//	    final List<String> hosts = Arrays.asList(_host2svrs.keySet().toArray(EMPTY_STRS));
	    final List<String> hosts = new ArrayList<>(
	            Sets.intersection(Sets.newHashSet(hostsAsString.split(",")),
	                    Sets.newHashSet(_host2svrs.keySet().toArray(EMPTY_STRS))));

	    Collections.sort(hosts);

        final Multimap<String, String> services = HashMultimap.create();

        for (final Map.Entry<String, List<String>> entry : _host2svrs.entrySet()) {
            if (hosts.contains(entry.getKey())) {
                for (final String srv : entry.getValue()) {
                    services.put(srv, entry.getKey());
                }
            }
        }

        final StringBuilder sb = new StringBuilder();
        String comma = "";

        sb.append('[');

        try (final Formatter formatter = new Formatter(sb)) {
            final Map<String, Collection<String>> srv2host = services.asMap();
            final List<String> srvs = Lists.newArrayList(srv2host.keySet().toArray(EMPTY_STRS));
            Collections.sort(srvs);

            for (final String service : srvs) {
                if (!this._ignores.contains(service)) {
                    formatter.format("%s[\"%s\",\"service\"]", comma, Strings.padEnd(service, 16, '_'));
                    comma = ",";

                    for (final String host : hosts) {
                        final Pair<ServiceInfo[], String> status = serviceStatus(service, host, srv2host.get(service));
                        formatter.format(",[\"%s\",\"%s\"]", hostWithBuildno(host, status.first), status.second);
                    }
                }
            }
        }
        sb.append(']');

        return sb.toString();
	}

    private String hostWithBuildno(final String host, final ServiceInfo[] infos) {
        String slash = "";
        final StringBuilder sb = new StringBuilder();
        if (null != infos) {
            for (final ServiceInfo info : infos) {
                final String build = extractBuild(info._buildNo);
                if (null != build) {
                    sb.append(slash);
                    sb.append(build);
                    slash = "/";
                }
            }
        }
        if (sb.length() > 0) {
            return Strings.padStart(sb.toString(), 20, '_');
        } else {
            return Strings.padStart(Strings.padEnd(host, 12, '_'), 20, '_');
        }
    }

    static private String extractBuild(final String buildNo) {
        final int start = buildNo.indexOf("-SNAPSHOT-");
        if (start >= 0) {
            return buildNo.substring(start + "-SNAPSHOT-".length());
        }
        return null;
    }

    private Pair<ServiceInfo[],String> serviceStatus(final String service, final String host, final Collection<String> installedHosts) {
        final List<ServiceInfo> infos = new ArrayList<>();
        if (installedHosts.contains(host)) {
            for (final ServiceInfo info : this._services) {
                if (info._service.equals(service) && info._hostname.equals(host)) {
                    infos.add(info);
                }
            }
            if (!infos.isEmpty()) {
                return Pair.of(infos.toArray(EMPTY_SRVINFOS), infos.size() == 1 ? "success" : "multi");
            } else {
                return Pair.of(null, "error");
            }
        } else {
            return Pair.of(null, "none");
        }
    }

    @Path("/app-status/checkServicesStatus")
    public String checkServicesStatus() {
        for (final Map.Entry<String, List<String>> entry : _host2svrs.entrySet()) {
            for (final String srv : entry.getValue()) {
                if (!this._ignores.contains(srv)) {
                    if (!isServiceRunning(srv, entry.getKey())) {
                        return "error";
                    }
                }
            }
        }

        return "success";
    }

    private boolean isServiceRunning(final String service, final String host) {
        for (final ServiceInfo info : this._services) {
            if (info._service.equals(service) && info._hostname.equals(host)) {
                return true;
            }
        }
        return false;
    }

    @Path("/app-status/report-srvs")
    public String reportServices(@QueryParam("srvs") final String srvs, @QueryParam("hostname") final String hostname) {
        _host2svrs.put(hostname, Arrays.asList(srvs.split(",")));
        return "OK";
    }

    @Inject
    BeanFinder _beanFinder;

    @Path("/rpc-status/rpcs")
    public Observable<String> listRpcs() {

        return this._beanFinder.find(DefaultEndpointSet.class).map(eps -> {
            final String[] types = eps.types();
            final StringBuilder sb = new StringBuilder();
            String comma = "";

            sb.append('[');

            try (final Formatter formatter = new Formatter(sb)) {
                for (final String type : types) {

                    formatter.format("%s[\"%s\",\"%s\"]", comma, Strings.padStart(type, 20, '.'),
                            eps.uris(type).length > 1 ? "multi" : "single");
                    comma = ",";
                }
            }
            sb.append(']');

            return sb.toString();
        });
    }
}
