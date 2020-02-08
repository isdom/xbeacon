package org.jocean.xbeacon.api;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.Formatter;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.inject.Inject;
import javax.inject.Named;
import javax.ws.rs.Path;
import javax.ws.rs.QueryParam;

import org.jocean.http.endpoint.internal.DefaultEndpointSet;
import org.jocean.idiom.BeanFinder;
import org.jocean.idiom.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Controller;

import com.alibaba.acm.shaded.com.google.common.collect.Lists;
import com.alibaba.fastjson.annotation.JSONField;
import com.google.common.base.Strings;
import com.google.common.collect.HashMultimap;
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

	@Path("/app-status/services")
	public String listServices(@QueryParam("hosts") final String hostsAsString,
	        @QueryParam("srvs") final String srvsAsString) {

	    final List<String> hosts = (null != hostsAsString && !hostsAsString.isEmpty())
	            ? new ArrayList<>(Sets.intersection(Sets.newHashSet(hostsAsString.split(",")),
    	                    Sets.newHashSet(_host2svrs.keySet().toArray(EMPTY_STRS))))
                : Arrays.asList(_host2svrs.keySet().toArray(EMPTY_STRS));
	    final List<String> srvPrefixs = (null != srvsAsString && !srvsAsString.isEmpty())
	            ? Arrays.asList(srvsAsString.split(",")) : null;

	    Collections.sort(hosts);

        final Multimap<String, String> services = HashMultimap.create();

        for (final Map.Entry<String, List<String>> entry : _host2svrs.entrySet()) {
            if (hosts.contains(entry.getKey())) {
                for (final String srv : entry.getValue()) {
                    if (startWithPrefixs(srvPrefixs, srv)) {
                        services.put(srv, entry.getKey());
                    }
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

            // add title bar
            formatter.format("%s[\"%s\",\"service\"]", comma, Strings.padEnd("<timestamp>", 16, '_'));
            comma = ",";

            final String now = Strings.padStart(Strings.padEnd(new SimpleDateFormat("HH:mm:ss:SSS").format(new Date()), 15, '_'), 20, '_');

            for (final String host : hosts) {
                formatter.format(",[\"%s\",\"%s\"]", now, "success");
            }

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

        LOG.info("hosts {}/srvs {}\r\n resp as :{}", hostsAsString, srvsAsString, sb.toString());

        return sb.toString();
	}

    static private boolean startWithPrefixs(final List<String> prefixs, final String srv) {
        if (null == prefixs) {
            return true;
        }
        for (final String prefix : prefixs) {
            if (srv.startsWith(prefix)) {
                return true;
            }
        }
        return false;
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
        else {
            return buildNo;
        }
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

    @Path("/app-status/servicesStatusDetail")
    public String servicesStatusDetail() {
        final StringBuilder sb = new StringBuilder();
        for (final Map.Entry<String, List<String>> entry : _host2svrs.entrySet()) {
            for (final String srv : entry.getValue()) {
                if (!this._ignores.contains(srv)) {
                    if (!isServiceRunning(srv, entry.getKey())) {
                        sb.append(entry.getKey()).append('/').append(srv).append(":!DOWN!").append('\n');
                    }
                    else {
                        sb.append(entry.getKey()).append('/').append(srv).append(":running").append('\n');
                    }
                }
            }
        }

        return sb.length() > 0 ? sb.toString() : "no service";
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

    @Path("/app-status/restins")
    public Object listRestins(@QueryParam("srv") final String service) {
        final List<OperationInfo> restins = new ArrayList<>();

        for (final OperationInfo info : this._restins) {
            if (null != service && !service.isEmpty()) {
                if (service.equals(info.getService())) {
                    restins.add(info);
                }
            }
            else {
                restins.add(info);
            }
        }
        Collections.sort(restins);

        return restins;
    }

    final static class Tab {
        @JSONField(name = "id")
        public int getId() {
            return _id;
        }

        @JSONField(name = "content")
        public String getContent() {
            return _content;
        }

        Tab(final int id, final String content) {
            _id = id;
            _content = content;
        }

        int _id;
        String _content;
    }

    @Path("/app-status/restin-services")
    public Object listRestinServices() {
        final List<String> services = new ArrayList<>();

        for (final OperationInfo info : this._restins) {
            if (!services.contains(info.getService())) {
                services.add(info.getService());
            }
        }
        Collections.sort(services);

        final List<Tab> tabs = new ArrayList<>();
        int idx = 1;
        for (final String srv : services) {
            tabs.add(new Tab(idx++, srv));
        }

        return tabs;
    }

    @Inject
    BeanFinder _beanFinder;

    @Inject
    @Named("services")
    List<ServiceInfo> _services;

    @Value("${ignores}")
    public void setIgnore(final String ignores) {
        this._ignores = Arrays.asList(ignores.split(","));
    }

    private List<String> _ignores = Collections.emptyList();

    Map<String, List<String>> _host2svrs = new ConcurrentHashMap<>();

    @Inject
    @Named("restins")
    List<OperationInfo> _restins;
}
