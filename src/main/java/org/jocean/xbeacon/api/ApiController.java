package org.jocean.xbeacon.api;

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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    Map<String, List<String>> _host2svrs = new ConcurrentHashMap<>();

	@Path("/app-status/services")
	public String listServices() {
	    final Multimap<String, String> services = HashMultimap.create();

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
            final List<String> srvs = Lists.newArrayList(srv2host.keySet().toArray(new String[0]));
            Collections.sort(srvs);

            for (final String service : srvs) {
                sb.append(comma);
                formatter.format("[\"%s\",\"service\"]", Strings.padEnd(service, 20, '_'));
                comma = ",";

                for (final String host : srv2host.get(service)) {
                    sb.append(comma);
                    formatter.format("[\"%s\",\"%s\"]", Strings.padEnd(host, 10, '_'),
                            isServiceRunning(service, host) ? "success" : "error");
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
