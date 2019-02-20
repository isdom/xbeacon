package org.jocean.xbeacon.api;

import org.springframework.beans.factory.annotation.Value;

public class ServiceInfo {

    @Value("${hostname}")
    String _hostname;

    @Value("${service}")
    String _service;

    @Value("${build.no}")
    String _buildNo;

    @Value("${jolokia.url}")
    String _jolokiaUrl;

    @Override
    public String toString() {
        final StringBuilder builder = new StringBuilder();
        builder.append("ServiceInfo [hostname=").append(_hostname).append(", service=").append(_service)
                .append(", buildNo=").append(_buildNo).append(", jolokiaUrl=").append(_jolokiaUrl).append("]");
        return builder.toString();
    }
}
