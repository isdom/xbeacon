package org.jocean.xbeacon.api;

import java.util.Arrays;
import java.util.Collection;
import java.util.Formatter;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.inject.Inject;
import javax.inject.Named;
import javax.ws.rs.Path;
import javax.ws.rs.QueryParam;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Controller;

import com.alibaba.fastjson.annotation.JSONField;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;
import com.google.common.collect.Sets;

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

    Map<String, List<String>> _host2svrs = new ConcurrentHashMap<>();

	@Path("/app-status/services")
	public String listServices() {
	    final Multimap<String, String> services = Multimaps.newSortedSetMultimap(Maps.newHashMap(), ()->Sets.newTreeSet());

	    for (final Map.Entry<String, List<String>> entry : _host2svrs.entrySet()) {
	        for (final String srv : entry.getValue()) {
	            services.put(srv, entry.getKey());
	        }
	    }

        final StringBuilder sb = new StringBuilder();
        String comma = "";

        sb.append('[');

        try (final Formatter formatter = new Formatter(sb)) {
            final Map<String, Collection<String>> srv2host = services.asMap();
            for (final Map.Entry<String, Collection<String>> entry2 : srv2host.entrySet()) {
                final String service = entry2.getKey();

                sb.append(comma);
                formatter.format("[\"%s10\",\"service\"]", service);
//                sb.append('[');
//                sb.append('"');
//                sb.append(service);
//                sb.append('"');
//                sb.append(',');
//                sb.append("\"service\"");
//                sb.append(']');
                comma = ",";

                for (final String host : entry2.getValue()) {
                    sb.append(comma);
                    formatter.format("[\"%s10\",\"%s\"]", host, isServiceRunning(service, host) ? "success" : "error");
//                    sb.append('[');
//                    sb.append('"');
//                    sb.append();
//                    sb.append('"');
//                    sb.append(',');
//                    sb.append('"');
//                    sb.append();
//                    sb.append('"');
//                    sb.append(']');
                }
            }
        }
        sb.append(']');

        return sb.toString();
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
}
