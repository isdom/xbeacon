package org.jocean.zookeeper.webui.admin;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.commons.io.FilenameUtils;
import org.apache.zookeeper.CreateMode;
import org.jocean.idiom.ExceptionUtils;
import org.jocean.idiom.Pair;
import org.jocean.j2se.unit.model.UnitDescription;
import org.jocean.j2se.zk.ZKAgent;
import org.jocean.j2se.zk.ZKAgent.Listener;
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

import rx.functions.Action1;

public class ZKTreeManager {
    
    private static final String EVENT_ZK_CHANGED = "zkChanged";
    private static final UnitDescription[] EMPTY_UNITDESCS = new UnitDescription[0];
    private static final byte[] EMPTY_BYTES = new byte[0];
    private static final String[] PATH_ROOT = new String[]{"/"};
    
    private static final Logger LOG = 
            LoggerFactory.getLogger(ZKTreeManager.class);

    public ZKTreeManager(final ZKAgent zkagent) {
        this._zkagent = zkagent;
        this._rootPath = zkagent.root();
//        this._rootNode = new SimpleTreeModel.Node(this._rootPath);
//        this._model = new ZKTreeModel(this._rootNode);
    }
    
    public SimpleTreeModel getModel() throws Exception {
        final ZKTreeModel model = new ZKTreeModel(new SimpleTreeModel.Node(this._rootPath));
        this._eventqueue.subscribe(new EventListener<Event>() {
            @SuppressWarnings("unchecked")
            @Override
            public void onEvent(final Event event) throws Exception {
                if ( event.getName().equals(EVENT_ZK_CHANGED)) {
                    ((Action1<ZKAgent.Listener>)event.getData()).call(model);
                }
            }});
        //	ensure clone invoke inside treecache's service executor
        //	so :
        //	1.  zk tree's model is consistent bcs of treecahce's update and zk tree node's update 
        //		is serialized
        //	2.  record this model's start tree version, so the early tree changed notify will be 
        //		ignore correct
//        modelref.set( 
//                this._executorService.submit(new Callable<ZKTreeModel>() {
//            @Override
//            public ZKTreeModel call() throws Exception {
//                return new ZKTreeModel(_rootNode.clone());
//            }} ).get());
        return model;
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
//    public void setZkclient(final CuratorFramework zkclient) {
//        this._zkagent = zkclient;
//    }
    
//    public void setRoot(final String rootPath) {
//        this._rootPath = rootPath;
//        this._rootNode = new SimpleTreeModel.Node(this._rootPath);
//        this._model = new ZKTreeModel(this._rootNode);
//    }
    
    public void start() throws Exception {
        this._eventqueue = EventQueues.lookup("zktree", this._webapp, true);
        this._stopListen = this._zkagent.addListener(new ZKAgent.Listener() {

            @Override
            public void onAdded(final int version, final String path, final byte[] data)
                    throws Exception {
                _eventqueue.publish(new Event(EVENT_ZK_CHANGED, null, new Action1<ZKAgent.Listener>() {
                    @Override
                    public void call(final Listener listener) {
                        try {
                            listener.onAdded(version, path, data);
                        } catch (Exception e) {
                            // TODO Auto-generated catch block
                            e.printStackTrace();
                        }
                    }}));
            }

            @Override
            public void onUpdated(final int version, final String path, final byte[] data)
                    throws Exception {
                _eventqueue.publish(new Event(EVENT_ZK_CHANGED, null, new Action1<ZKAgent.Listener>() {
                    @Override
                    public void call(final Listener listener) {
                        try {
                            listener.onUpdated(version, path, data);
                        } catch (Exception e) {
                            // TODO Auto-generated catch block
                            e.printStackTrace();
                        }
                    }}));
            }

            @Override
            public void onRemoved(final int version, final String path) throws Exception {
                _eventqueue.publish(new Event(EVENT_ZK_CHANGED, null, new Action1<ZKAgent.Listener>() {
                    @Override
                    public void call(final Listener listener) {
                        try {
                            listener.onRemoved(version, path);
                        } catch (Exception e) {
                            // TODO Auto-generated catch block
                            e.printStackTrace();
                        }
                    }}));
            }});
        /*
        this._treecache = TreeCache.newBuilder(_zkagent, _rootPath)
                .setCacheData(true)
                .setExecutor(this._executorService)
                .build();
        this._treecache.getListenable().addListener(new TreeCacheListener() {
            @Override
            public void childEvent(final CuratorFramework client, final TreeCacheEvent event)
                    throws Exception {
                _treeVersion++;
                _model.onZKChanged(_treeVersion, event);
                _eventqueue.publish(new Event(EVENT_ZK_CHANGED, null, Pair.of(_treeVersion, event)));
            }});
        this._treecache.start();
        */
    }
    
    public void stop() {
        this._stopListen.run();
//        this._treecache.close();
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
        this._zkagent.client().setData().forPath(path, data.getBytes(Charsets.UTF_8));
    }
    
    public String createZKNode(final String nodepath, final byte[] nodecontent) throws Exception {
        return this._zkagent.client().create().forPath(nodepath, nodecontent);
    }
    
    public void removeZKNode(final String nodepath) throws Exception {
        _zkagent.client().delete()
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

    class ZKTreeModel extends SimpleTreeModel implements ZKAgent.Listener {

        private static final long serialVersionUID = 1L;

        public ZKTreeModel(final Node root) {
            super(root);
        }
        
//        private final int _startVersion = _treeVersion;

        @Override
        public void onAdded(final int version, final String path, final byte[] data)
                throws Exception {
            final String[] paths = buildPath(path);
            if (LOG.isDebugEnabled()) {
                LOG.debug("onNodeAdded: {}", Arrays.toString(paths));
            }
            final SimpleTreeModel.Node node = 
                    this.getRoot().addChildrenIfAbsent(paths);
            node.setData(Pair.of(path, null != data
                                    ? new String(data, Charsets.UTF_8)
                                    : null));
            final SimpleTreeModel.Node parent = node.getParent();
            fireEvent(TreeDataEvent.INTERVAL_ADDED, 
                    this.getPath(parent),
                    this.getIndexOfChild(parent, node),
                    parent.getChildCount() - 1,
                    this.getPath(node)
                    );
        }

        @Override
        public void onUpdated(final int version, final String path, final byte[] data)
                throws Exception {
            final String[] paths = buildPath(path);
            if (LOG.isDebugEnabled()) {
                LOG.debug("onNodeUpdated: {}", Arrays.toString(paths));
            }
            final SimpleTreeModel.Node node = 
                    this.getRoot().getDescendant(paths);
            if (null!=node) {
                node.setData(Pair.of(path, null != data 
                                        ? new String(data, Charsets.UTF_8)
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

        @Override
        public void onRemoved(int version, String path) throws Exception {
            final String[] paths = buildPath(path);
            if (LOG.isDebugEnabled()) {
                LOG.debug("onNodeRemoved: {}", Arrays.toString(paths));
            }
            final SimpleTreeModel.Node node = this.getRoot().getDescendant(paths);
            if (null!=node) {
                final int[] affectedPath = this.getPath(node);
                final int idx = this.getIndexOfChild(node.getParent(), node);
                final SimpleTreeModel.Node parent = node.getParent();
                this.getRoot().removeChild(paths);
                fireEvent(TreeDataEvent.INTERVAL_REMOVED, 
                        this.getPath(parent),
                        idx,
                        idx,
                        affectedPath);
            }
        }
    }

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
            return 0 != this._zkagent.client().checkExists().forPath(path).getEphemeralOwner();
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
        return this._zkagent.client().create()
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
    
//    private final ExecutorService _executorService = Executors.newSingleThreadExecutor(new ThreadFactory() {
//        @Override
//        public Thread newThread(Runnable r) {
//            final Thread thread = new Thread(r);
//            thread.setName("zkagent-0");
//            return thread;
//        }});
    private ZKAgent _zkagent;
    private Runnable _stopListen;
    private WebApp _webapp;
    private String _rootPath;
    private EventQueue<Event> _eventqueue;
    
//    private SimpleTreeModel.Node _rootNode;
//    private ZKTreeModel _model;
}
