package org.jocean.zookeeper.webui.admin;

import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.recipes.cache.TreeCache;
import org.apache.curator.framework.recipes.cache.TreeCacheEvent;
import org.apache.curator.framework.recipes.cache.TreeCacheListener;
import org.jocean.zkoss.model.SimpleTreeModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zkoss.zk.ui.WebApp;
import org.zkoss.zk.ui.event.Event;
import org.zkoss.zk.ui.event.EventQueue;
import org.zkoss.zk.ui.event.EventQueues;

public class ZKAgent {
    
    private static final Logger LOG = 
            LoggerFactory.getLogger(ZKAgent.class);

    /**
     * @param _webapp the _webapp to set
     */
    public void setWebapp(final WebApp webapp) {
        this._webapp = webapp;
    }

    public SimpleTreeModel getModel() {
        return new SimpleTreeModel(this._rootNode);
    }
    
    /**
     * @param zkclient the _zkclient to set
     */
    public void setZkclient(final CuratorFramework zkclient) {
        this._zkclient = zkclient;
    }
    
    public void setRoot(final String rootPath) {
        this._rootPath = rootPath;
        this._rootNode = new SimpleTreeModel.Node(this._rootPath);
    }
    
    public void start() throws Exception {
        this._eventqueue = EventQueues.lookup("zktree", this._webapp, true);
        this._treecache = TreeCache.newBuilder(_zkclient, _rootPath)
                .setCacheData(true)
                .build();
        this._treecache.getListenable().addListener(new TreeCacheListener() {

            @Override
            public void childEvent(CuratorFramework client, TreeCacheEvent event)
                    throws Exception {
                switch (event.getType()) {
                case NODE_ADDED:
                    addNode(event);
                    break;
                case NODE_REMOVED:
                    removeNode(event);
                    break;
                case NODE_UPDATED:
                default:
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("unhandle event ({}), just ignore.", 
                                event);
                    }
                    break;
                }
            }});
        this._treecache.start();
    }
    
    public void stop() {
        this._treecache.close();
    }

    private void addNode(final TreeCacheEvent event) {
        _rootNode.addChildrenIfAbsent(event.getData().getPath().split("/"));
    }

    private void removeNode(final TreeCacheEvent event) {
        // TODO Auto-generated method stub
        
    }


    private TreeCache _treecache;
    private CuratorFramework _zkclient;
    private WebApp _webapp;
    private String _rootPath;
    private EventQueue<Event> _eventqueue;
    
    private SimpleTreeModel.Node _rootNode;
}
