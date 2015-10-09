package org.jocean.zookeeper.webui.admin;

import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.recipes.cache.TreeCache;
import org.apache.curator.framework.recipes.cache.TreeCacheEvent;
import org.apache.curator.framework.recipes.cache.TreeCacheListener;
import org.jocean.idiom.Pair;
import org.jocean.zkoss.model.SimpleTreeModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zkoss.zk.ui.WebApp;
import org.zkoss.zk.ui.event.Event;
import org.zkoss.zk.ui.event.EventQueue;
import org.zkoss.zk.ui.event.EventQueues;

import com.google.common.base.Charsets;

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
                    updateNode(event);
                    break;
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

    @SuppressWarnings("unchecked")
    public String getNodeDataAsString(final SimpleTreeModel.Node node) {
        return ((Pair<String,String>)node.getData()).second;
    }
    
    public void setNodeDataAsString(final SimpleTreeModel.Node node, final String data) 
            throws Exception {
        @SuppressWarnings("unchecked")
        final String path = ((Pair<String,String>)node.getData()).first;
        this._zkclient.setData().forPath(path, data.getBytes(Charsets.UTF_8));
    }
    
    private void addNode(final TreeCacheEvent event) {
        final SimpleTreeModel.Node node = 
                this._rootNode.addChildrenIfAbsent(event.getData().getPath().split("/"));
        node.setData(Pair.of(event.getData().getPath(), 
                null != event.getData().getData() 
                    ? new String(event.getData().getData(), Charsets.UTF_8)
                    : null));
        notifyModelChanged(event.getData().getPath());
    }

    private void removeNode(final TreeCacheEvent event) {
        if (null!=this._rootNode.removeChild(event.getData().getPath().split("/"))) {
            notifyModelChanged(event.getData().getPath());
        }
    }

    private void updateNode(final TreeCacheEvent event) {
        final SimpleTreeModel.Node node = 
                this._rootNode.getDescendant(event.getData().getPath().split("/"));
        if (null!=node) {
            node.setData(Pair.of(event.getData().getPath(), 
                    null != event.getData().getData() 
                        ? new String(event.getData().getData(), Charsets.UTF_8)
                        : null));
            notifyModelChanged(event.getData().getPath());
        }
    }

    private void notifyModelChanged(final String path) {
        this._eventqueue.publish(new Event("modelChanged"));
    }

    private TreeCache _treecache;
    private CuratorFramework _zkclient;
    private WebApp _webapp;
    private String _rootPath;
    private EventQueue<Event> _eventqueue;
    
    private SimpleTreeModel.Node _rootNode;
}
