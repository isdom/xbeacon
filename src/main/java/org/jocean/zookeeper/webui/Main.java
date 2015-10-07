/**
 * 
 */
package org.jocean.zookeeper.webui;

import java.io.File;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.webapp.WebAppContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * @author Marvin.Ma
 *
 */
public class Main {

    private static final Logger LOG = 
            LoggerFactory.getLogger(Main.class);
    
	/**
	 * @param args
	 * @throws Exception 
	 */
	public static void main(String[] args) throws Exception {
        final Server server = new Server(7080);
        
        final WebAppContext context = new WebAppContext();
        context.setContextPath("/zkwebui");
        
        final String warfile = System.getProperty("user.dir") 
                + System.getProperty("file.separator")
                + "bin"
                + System.getProperty("file.separator")
                + System.getProperty("app.name", "jocean-zk-webui")
                + ".jar"
                ;
        if ( new File(warfile).exists() ) {
            LOG.info("found warfile {}, and use this file as war", warfile);
            final File tmpdir = new File(System.getProperty("user.home") 
                    + System.getProperty("file.separator")
                    + ".zkwebui");
            if (!tmpdir.exists()) {
                tmpdir.mkdirs();
            }
            context.setTempDirectory(tmpdir);
            context.setWar(warfile);
        }
        else {
            LOG.info("can't found warfile {}, try default web content info", warfile);
            context.setDescriptor( "scripts/webcontent/WEB-INF/web.xml");
            context.setResourceBase( "scripts/webcontent/");
            context.setParentLoaderPriority(true);
        }
        
        server.setHandler(context);
 
        server.start();
        server.join();
	}

}
