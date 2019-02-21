package org.jocean.xbeacon.api;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Formatter;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.inject.Inject;
import javax.inject.Named;
import javax.ws.rs.Path;
import javax.ws.rs.QueryParam;

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

@Controller
@Scope("singleton")
public class ApiController {

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

	@SuppressWarnings("unused")
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

	@Path("/app-status/services")
	public String listServices() {
	    final Multimap<String, String> services = HashMultimap.create();

	    for (final Map.Entry<String, List<String>> entry : _host2svrs.entrySet()) {
	        for (final String srv : entry.getValue()) {
	            services.put(srv, entry.getKey());
	        }
	    }
	    final List<String> hosts = Arrays.asList(_host2svrs.keySet().toArray(new String[0]));
	    Collections.sort(hosts);

        final StringBuilder sb = new StringBuilder();
        String comma = "";

        sb.append('[');

        try (final Formatter formatter = new Formatter(sb)) {
            final Map<String, Collection<String>> srv2host = services.asMap();
            final List<String> srvs = Lists.newArrayList(srv2host.keySet().toArray(new String[0]));
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
            return host + Strings.padStart("[" + sb.toString() + "]", 12 - host.length(), '_');
        } else {
            return Strings.padEnd(host, 12, '_');
        }
    }

    static private String extractBuild(final String buildNo) {
        final int start = buildNo.indexOf("-SNAPSHOT-");
        if (start >= 0) {
            final int end = buildNo.lastIndexOf("-");
            if (end > start) {
                return buildNo.substring(start + "-SNAPSHOT-".length(), end);
            }
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
                return Pair.of(infos.toArray(new ServiceInfo[0]), infos.size() == 1 ? "success" : "multi");
            } else {
                return Pair.of(null, "error");
            }
        } else {
            return Pair.of(null, "none");
        }
    }

    @Path("/app-status/report-srvs")
    public String reportServices(@QueryParam("srvs") final String srvs, @QueryParam("hostname") final String hostname) {
        _host2svrs.put(hostname, Arrays.asList(srvs.split(",")));
        return "OK";
    }
}
