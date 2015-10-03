package org.jocean.zookeeper.webui;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Demo {
    private static final Logger LOG = 
            LoggerFactory.getLogger(Demo.class);
    public Demo() {
        LOG.info("demo started...");
    }
    
    public String getMessage() {
        return "Demo";
    }
}
