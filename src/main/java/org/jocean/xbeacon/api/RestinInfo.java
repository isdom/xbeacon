package org.jocean.xbeacon.api;

import org.springframework.beans.factory.annotation.Value;

public class RestinInfo {

    @Value("${hostname}")
    String _hostname;

    @Value("${service}")
    String _service;

    @Value("${pid}")
    String _pid;

    @Value("${ip}")
    String _ip;

    @Value("${port}")
    int _port;

    @Value("${trade.count}")
    int _tradeCount;
}
