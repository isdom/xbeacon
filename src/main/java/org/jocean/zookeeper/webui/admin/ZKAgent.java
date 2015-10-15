package org.jocean.zookeeper.webui.admin;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.commons.io.FilenameUtils;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.recipes.cache.TreeCache;
import org.apache.curator.framework.recipes.cache.TreeCacheEvent;
import org.apache.curator.framework.recipes.cache.TreeCacheListener;
import org.jocean.idiom.ExceptionUtils;
import org.jocean.idiom.Pair;
import org.jocean.j2se.unit.model.UnitDescription;
import org.jocean.zkoss.model.SimpleTreeModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zkoss.zk.ui.WebApp;
import org.zkoss.zk.ui.event.Event;
import org.zkoss.zk.ui.event.EventQueue;
import org.zkoss.zk.ui.event.EventQueues;

import com.google.common.base.Charsets;

public class ZKAgent {
    
    private static final UnitDescription[] EMPTY_UNITDESCS = new UnitDescription[0];
    private static final String[] PATH_ROOT = new String[]{"/"};
    private static final Logger LOG = 
            LoggerFactory.getLogger(ZKAgent.class);

    public SimpleTreeModel getModel() {
        return new SimpleTreeModel(this._rootNode);
    }
    
    /**
     * @param _webapp the _webapp to set
     */
    public void setWebapp(final WebApp webapp) {
        this._webapp = webapp;
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
    
    @SuppressWarnings("unchecked")
    public String getNodePath(final SimpleTreeModel.Node node) {
        return ((Pair<String,String>)node.getData()).first;
    }
    
    public void setNodeDataAsString(final SimpleTreeModel.Node node, final String data) 
            throws Exception {
        @SuppressWarnings("unchecked")
        final String path = ((Pair<String,String>)node.getData()).first;
        this._zkclient.setData().forPath(path, data.getBytes(Charsets.UTF_8));
    }
    
    public String createZKNode(final String nodepath, final byte[] nodecontent) throws Exception {
        return this._zkclient.create().forPath(nodepath, nodecontent);
    }
    
    public void removeZKNode(final String nodepath) throws Exception {
        _zkclient.delete()
            .deletingChildrenIfNeeded()
            .forPath(nodepath);
    }
    
    private static String[] buildPath(final TreeCacheEvent event) {
        final String rawpath = event.getData().getPath();
        final String[] path = rawpath.split("/");
        if ( 0 == path.length) {
            return PATH_ROOT;
        } else {
            path[0] = "/";
            return path;
        }
    }

    private void addNode(final TreeCacheEvent event) {
        final String[] path = buildPath(event);
        if (LOG.isDebugEnabled()) {
            LOG.debug("addNode: {}", Arrays.toString(path));
        }
        final SimpleTreeModel.Node node = 
                this._rootNode.addChildrenIfAbsent(path);
        node.setData(Pair.of(event.getData().getPath(), 
                null != event.getData().getData() 
                    ? new String(event.getData().getData(), Charsets.UTF_8)
                    : null));
        notifyModelChanged(event.getData().getPath());
    }

    private void removeNode(final TreeCacheEvent event) {
        if (null!=this._rootNode.removeChild(buildPath(event))) {
            notifyModelChanged(event.getData().getPath());
        }
    }

    private void updateNode(final TreeCacheEvent event) {
        final SimpleTreeModel.Node node = 
                this._rootNode.getDescendant(buildPath(event));
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

    public UnitDescription node2desc(final SimpleTreeModel.Node node) {
        try {
            return dumpNode(getNodePath(node));
        } catch (Exception e) {
            LOG.warn("exception when dumpNode for node {}, detail: {}",
                    node, ExceptionUtils.exception2detail(e));
            return null;
        }
    }
    
    private UnitDescription dumpNode(final String path) throws Exception {
        final UnitDescription desc = dumpContent(path);
        if (null!=desc) {
            final List<String> children = this._zkclient.getChildren().forPath(path);
            final List<UnitDescription>  descs = new ArrayList<>();
            for (String child : children) {
                final UnitDescription childDesc = dumpNode(path + "/" + child);
                if (null!=childDesc) {
                    descs.add(childDesc);
                } else {
                    System.out.println(path + "/" + child + " is EphemeralNode, not export.");
                }
            }
            if (!descs.isEmpty()) {
                desc.setChildren(descs.toArray(EMPTY_UNITDESCS));
            }
        }
        return desc;
    }

    private UnitDescription dumpContent(final String path) throws Exception {
        if (isEphemeralNode(path) ) {
            return null;
        }
        final UnitDescription desc = new UnitDescription();
        desc.setName(FilenameUtils.getName(path));
        final byte[] content = this._zkclient.getData().forPath(path);
        if (null != content) {
            desc.setParameters(new String(content, Charsets.UTF_8));
        }
        return desc;
    }

    private boolean isEphemeralNode(final String path) throws Exception {
        return 0 != this._zkclient.checkExists().forPath(path).getEphemeralOwner();
    }

    private TreeCache _treecache;
    private CuratorFramework _zkclient;
    private WebApp _webapp;
    private String _rootPath;
    private EventQueue<Event> _eventqueue;
    
    private SimpleTreeModel.Node _rootNode;
}
