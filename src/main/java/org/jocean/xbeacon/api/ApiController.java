package org.jocean.xbeacon.api;

import java.util.List;

import javax.inject.Inject;
import javax.inject.Named;
import javax.ws.rs.Path;
import javax.ws.rs.QueryParam;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Controller;

@Controller
@Scope("singleton")
public class ApiController {

	@SuppressWarnings("unused")
    private static final Logger LOG = LoggerFactory.getLogger(ApiController.class);

    @Inject
    @Named("services")
    List<ServiceInfo> _services;

	@Path("/app-status/services")
	public String listServices() {
	    final StringBuilder sb = new StringBuilder();
	    for (final ServiceInfo srv : _services) {
	        sb.append(srv.toString());
	        sb.append("\r\n");
	    }
        return sb.toString();
	}

    @Path("/app-status/report-srvs")
    public String reportServices(@QueryParam("srvs") final String srvs, @QueryParam("hostname") final String hostname) {
        return hostname + ":" + srvs;
    }
}
