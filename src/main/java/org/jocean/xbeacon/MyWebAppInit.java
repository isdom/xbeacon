/**
 * 
 */
package org.jocean.xbeacon;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zkoss.zk.ui.WebApp;
import org.zkoss.zk.ui.util.WebAppInit;

/**
 * @author isdom
 *
 */
public class MyWebAppInit implements WebAppInit {

    private static final Logger LOG = 
            LoggerFactory.getLogger(MyWebAppInit.class);
    
    private static WebApp _webapp;
    
	/* (non-Javadoc)
	 * @see org.zkoss.zk.ui.util.WebAppInit#init(org.zkoss.zk.ui.WebApp)
	 */
	@Override
	public void init(final WebApp webapp) throws Exception {
	    LOG.info("webapp {} init.", webapp);
	    _webapp = webapp;
	}
	
	public static WebApp getWebapp() {
	    return _webapp;
	}
}
