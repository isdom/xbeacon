package org.jocean.zookeeper.webui.admin;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.commons.io.FilenameUtils;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.recipes.cache.TreeCache;
import org.apache.curator.framework.recipes.cache.TreeCacheEvent;
import org.apache.curator.framework.recipes.cache.TreeCacheListener;
import org.apache.zookeeper.CreateMode;
import org.jocean.idiom.ExceptionUtils;
import org.jocean.idiom.Pair;
import org.jocean.j2se.unit.model.UnitDescription;
import org.jocean.zkoss.model.SimpleTreeModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zkoss.zk.ui.WebApp;
import org.zkoss.zk.ui.event.Event;
import org.zkoss.zk.ui.event.EventListener;
import org.zkoss.zk.ui.event.EventQueue;
import org.zkoss.zk.ui.event.EventQueues;
import org.zkoss.zul.event.TreeDataEvent;

import com.google.common.base.Charsets;

public class ZKAgent {
    
    private static final UnitDescription[] EMPTY_UNITDESCS = new UnitDescription[0];
    private static final byte[] EMPTY_BYTES = new byte[0];
    private static final String[] PATH_ROOT = new String[]{"/"};
    
    private static final Logger LOG = 
            LoggerFactory.getLogger(ZKAgent.class);

    public SimpleTreeModel getModel() throws Exception {
        final AtomicReference<ZKTreeModel> modelref = new AtomicReference<>();
        this._eventqueue.subscribe(new EventListener<Event>() {
            @Override
            public void onEvent(final Event event) throws Exception {
                if ( event.getName().equals("zkChanged")) {
                    @SuppressWarnings("unchecked")
                    final Pair<Integer, TreeCacheEvent> pair = (Pair<Integer, TreeCacheEvent>)event.getData();
                    modelref.get().onZKChanged(pair.first, pair.second);
                }
            }});
        modelref.set( 
                this._executorService.submit(new Callable<ZKTreeModel>() {
            @Override
            public ZKTreeModel call() throws Exception {
                return new ZKTreeModel(_rootNode.clone());
            }} ).get());
        return modelref.get();
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
        this._model = new ZKTreeModel(this._rootNode);
    }
    
    public void start() throws Exception {
        this._eventqueue = EventQueues.lookup("zktree", this._webapp, true);
        this._treecache = TreeCache.newBuilder(_zkclient, _rootPath)
                .setCacheData(true)
                .setExecutor(this._executorService)
                .build();
        this._treecache.getListenable().addListener(new TreeCacheListener() {
            @Override
            public void childEvent(CuratorFramework client, TreeCacheEvent event)
                    throws Exception {
                _treeVersion++;
                _model.onZKChanged(_treeVersion, event);
                _eventqueue.publish(new Event("zkChanged", null, Pair.of(_treeVersion, event)));
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
    
    private static String[] buildPath(final String rawpath) {
        final String[] path = rawpath.split("/");
        if ( 0 == path.length) {
            return PATH_ROOT;
        } else {
            path[0] = "/";
            return path;
        }
    }

    class ZKTreeModel extends SimpleTreeModel {

        private static final long serialVersionUID = 1L;

        public ZKTreeModel(final Node root) {
            super(root);
        }
        
        void onZKChanged(final int version, final TreeCacheEvent event) {
            if (version <= this._startVersion) {
                //  just ignore
                return;
            }
            switch (event.getType()) {
            case NODE_ADDED:
                onNodeAdded(event);
                break;
            case NODE_REMOVED:
                onNodeRemoved(event);
                break;
            case NODE_UPDATED:
                onNodeUpdated(event);
                break;
            default:
                if (LOG.isDebugEnabled()) {
                    LOG.debug("unhandle event ({}), just ignore.", 
                            event);
                }
                break;
            }
        }
        
        private void onNodeAdded(final TreeCacheEvent event) {
            final String[] path = buildPath(event.getData().getPath());
            if (LOG.isDebugEnabled()) {
                LOG.debug("onNodeAdded: {}", Arrays.toString(path));
            }
            final SimpleTreeModel.Node node = 
                    this.getRoot().addChildrenIfAbsent(path);
            node.setData(Pair.of(event.getData().getPath(), 
                    null != event.getData().getData() 
                        ? new String(event.getData().getData(), Charsets.UTF_8)
                        : null));
            final SimpleTreeModel.Node parent = node.getParent();
            fireEvent(TreeDataEvent.INTERVAL_ADDED, 
                    this.getPath(parent),
                    this.getIndexOfChild(parent, node),
                    parent.getChildCount() - 1,
                    this.getPath(node)
                    );
        }

        private void onNodeRemoved(final TreeCacheEvent event) {
            final String[] path = buildPath(event.getData().getPath());
            if (LOG.isDebugEnabled()) {
                LOG.debug("onNodeRemoved: {}", Arrays.toString(path));
            }
            final SimpleTreeModel.Node node = this.getRoot().getDescendant(path);
            if (null!=node) {
                final int[] affectedPath = this.getPath(node);
                final int idx = this.getIndexOfChild(node.getParent(), node);
                final SimpleTreeModel.Node parent = node.getParent();
                this.getRoot().removeChild(path);
                fireEvent(TreeDataEvent.INTERVAL_REMOVED, 
                        this.getPath(parent),
                        idx,
                        idx,
                        affectedPath);
//                notifyZKChanged("nodeRemoved", getNodePath(node));
            }
        }

        private void onNodeUpdated(final TreeCacheEvent event) {
            final String[] path = buildPath(event.getData().getPath());
            if (LOG.isDebugEnabled()) {
                LOG.debug("onNodeUpdated: {}", Arrays.toString(path));
            }
            final SimpleTreeModel.Node node = 
                    this.getRoot().getDescendant(path);
            if (null!=node) {
                node.setData(Pair.of(event.getData().getPath(), 
                        null != event.getData().getData() 
                            ? new String(event.getData().getData(), Charsets.UTF_8)
                            : null));
                final SimpleTreeModel.Node parent = node.getParent();
                final int idx = this.getIndexOfChild(parent, node);
                fireEvent(TreeDataEvent.CONTENTS_CHANGED,
                        this.getPath(parent),
                        idx,
                        idx,
                        this.getPath(node));
            }
        }
        
        private final int _startVersion = _treeVersion;
    }

//    private void notifyModelChanged(final TreeDataEvent treeDataEvent) {
//        this._eventqueue.publish(new Event("modelChanged", null, treeDataEvent));
//    }
//
//    private void notifyZKChanged(final String event, final String nodePath) {
//        this._eventqueue.publish(new Event(event, null, nodePath));
//    }

    public UnitDescription node2desc(final SimpleTreeModel.Node node) {
        try {
            return dumpNode(node);
        } catch (Exception e) {
            LOG.warn("exception when dumpNode for node {}, detail: {}",
                    node, ExceptionUtils.exception2detail(e));
            return null;
        }
    }
    
    private UnitDescription dumpNode(final SimpleTreeModel.Node node) {
        final UnitDescription desc = dumpContent(node);
        if (null!=desc) {
            final List<UnitDescription>  descs = new ArrayList<>();
            for (int idx = 0; idx < node.getChildCount(); idx++) {
                final SimpleTreeModel.Node child = node.getChild(idx);
                final UnitDescription childDesc = dumpNode(child);
                if (null!=childDesc) {
                    descs.add(childDesc);
                } else {
                    LOG.info("{}/{} is EphemeralNode, not export.", node, child);
                }
            }
            if (!descs.isEmpty()) {
                desc.setChildren(descs.toArray(EMPTY_UNITDESCS));
            }
        }
        return desc;
    }

    private UnitDescription dumpContent(final SimpleTreeModel.Node node) {
        final String path = this.getNodePath(node);
        if (isEphemeralNode(path) ) {
            return null;
        }
        final UnitDescription desc = new UnitDescription();
        desc.setName(FilenameUtils.getName(path));
        final String content = this.getNodeDataAsString(node);
        if (null != content) {
            desc.setParameters(content);
        }
        return desc;
    }

    private boolean isEphemeralNode(final String path) {
        try {
            return 0 != this._zkclient.checkExists().forPath(path).getEphemeralOwner();
        } catch (Exception e) {
            LOG.warn("exception when isEphemeralNode for {}, detail:{}",
                    path, ExceptionUtils.exception2detail(e));
            return false;
        }
    }

    public String importNode(final String path, final UnitDescription desc) {
        try {
            final String createdPath = importContent(path, desc);
            for (UnitDescription child : desc.getChildren()) {
                importNode(createdPath, child);
            }
            return createdPath;
        } catch (Exception e) {
            LOG.warn("exception when importNode for path {}, detail: {}",
                    path, ExceptionUtils.exception2detail(e));
            return null;
        }
    }

    private String importContent(final String path, final UnitDescription desc) 
            throws Exception {
        return this._zkclient.create()
                .creatingParentsIfNeeded()
                .withMode(CreateMode.PERSISTENT)
                .forPath(path + "/" + desc.getName(), 
                        genBytes(desc.getParameters()));
    }

    private static byte[] genBytes(final String parameters) {
        if (null!=parameters) {
            return parameters.getBytes(Charsets.UTF_8);
        } else {
            return EMPTY_BYTES;
        }
    }
    
    private final ExecutorService _executorService = Executors.newSingleThreadExecutor(new ThreadFactory() {
        @Override
        public Thread newThread(Runnable r) {
            final Thread thread = new Thread(r);
            thread.setName("zkagent-0");
            return thread;
        }});
    private TreeCache _treecache;
    private int _treeVersion = 0;
    private CuratorFramework _zkclient;
    private WebApp _webapp;
    private String _rootPath;
    private EventQueue<Event> _eventqueue;
    
    private SimpleTreeModel.Node _rootNode;
    private ZKTreeModel _model;
}
