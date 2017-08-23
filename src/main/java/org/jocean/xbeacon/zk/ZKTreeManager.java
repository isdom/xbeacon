package org.jocean.xbeacon.zk;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.apache.commons.io.FilenameUtils;
import org.apache.curator.framework.CuratorFramework;
import org.apache.zookeeper.CreateMode;
import org.jocean.idiom.ExceptionUtils;
import org.jocean.idiom.Pair;
import org.jocean.j2se.unit.model.UnitDescription;
import org.jocean.j2se.zk.ZKAgent;
import org.jocean.zkoss.model.SimpleTreeModel;
import org.jocean.zkoss.util.Desktops;
import org.jocean.zkoss.util.EventQueueForwarder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zkoss.zk.ui.WebApp;
import org.zkoss.zk.ui.event.Event;
import org.zkoss.zk.ui.event.EventQueue;
import org.zkoss.zk.ui.event.EventQueues;
import org.zkoss.zul.event.TreeDataEvent;

import com.google.common.base.Charsets;
import com.google.common.collect.Lists;
import com.google.common.util.concurrent.ThreadFactoryBuilder;

import rx.functions.Action0;

public class ZKTreeManager {
    
    public class ModelSource {
        public ModelSource(final ZKTreeModel model,
                final EventQueue<Event> eq) {
            this._model = model;
            this._eqf = new EventQueueForwarder<>(ZKAgent.Listener.class, eq);
            this._eqf.subscribe(model);
        }
        
        public SimpleTreeModel model() {
            return this._model;
        }
        
        public void attachToZKAgent(final ZKAgent agent) {
            this._detachers.add(agent.addListener(this._eqf.subject()));
        }
        
        public void close() {
            for (Action0 detacher : this._detachers) {
                detacher.call();
            }
            this._detachers.clear();
        }
        
        private final ZKTreeModel _model;
        private final EventQueueForwarder<ZKAgent.Listener> _eqf;
        private final List<Action0> _detachers = new ArrayList<>();
    }
    
    private static final UnitDescription[] EMPTY_UNITDESCS = new UnitDescription[0];
    private static final byte[] EMPTY_BYTES = new byte[0];
    private static final String[] PATH_ROOT = new String[]{"/"};
    
    private static final Logger LOG = 
            LoggerFactory.getLogger(ZKTreeManager.class);

    public ZKTreeManager(final CuratorFramework client) {
        this._client = client;
        this._executor = Executors.newSingleThreadExecutor(new ThreadFactoryBuilder()
                .setNameFormat("ZKTreeManager-%d")
                .setDaemon(false)
                .build());
    }
    
    public ModelSource getModel() throws Exception {
        final ModelSource source = new ModelSource(new ZKTreeModel(new SimpleTreeModel.Node("/")), 
                this._eventqueue);
            
        this._executor.submit(new Runnable() {
            @Override
            public void run() {
                addModel(source);
            }});
        Desktops.addActionForCurrentDesktopCleanup(new Action0() {
            @Override
            public void call() {
                source.close();
            }});
        return source;
    }
    
    private void addModel(final ModelSource source) {
        this._models.add(source);
        for (ZKAgent zka : this._zkagents) {
            source.attachToZKAgent(zka);
        }
    }

    public Action0 addZKAgent(final ZKAgent zkagent) {
        this._executor.submit(new Runnable() {
            @Override
            public void run() {
                doAddZKAgent(zkagent);
            }});
        return new Action0() {
            @Override
            public void call() {
                _executor.submit(new Runnable() {
                    @Override
                    public void run() {
                        doRemoveZKAgent(zkagent);
                    }});
            }
        };
    }
    
    private void doAddZKAgent(final ZKAgent zkagent) {
        this._zkagents.add(zkagent);
        for (ModelSource source : this._models) {
            source.attachToZKAgent(zkagent);
        }
    }
    
    private void doRemoveZKAgent(final ZKAgent zkagent) {
        this._zkagents.remove(zkagent);        
    }

    public Action0 setWebapp(final WebApp webapp) {
        this._webapp = webapp;
        this._eventqueue = EventQueues.lookup("zktree", this._webapp, true);
        LOG.info("ZKTreeManager.setWebapp with webapp({}) and create eventqueue({})", 
                webapp, this._eventqueue);
        return new Action0() {
            @Override
            public void call() {
                EventQueues.remove("zktree", _webapp);
                // TODO, zkagent.addListener(this._eqf.subject())'s call to remove Listener
            }};
    }

//    public void start() throws Exception {
//    }
    
    public void stop() {
        EventQueues.remove("zktree", this._webapp);
        this._executor.shutdownNow();
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
        this._client.setData()
            .forPath(path, data.getBytes(Charsets.UTF_8));
    }
    
    public String createZKNode(final String nodepath, final byte[] nodecontent) throws Exception {
        return this._client.create()
                .forPath(nodepath, nodecontent);
    }
    
    public void removeZKNode(final String nodepath) throws Exception {
        this._client.delete()
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
        
        @Override
        public void onAdded(final ZKAgent agent, 
                final String absolutepath, 
                final byte[] data) throws Exception {
            if (!isManagedPath(absolutepath)) {
                return;
            }
            final String path = absolute2relative(absolutepath);
            final String[] paths = buildPath(path);
            if (LOG.isDebugEnabled()) {
                LOG.debug("onNodeAdded: {}", Arrays.toString(paths));
            }
            
            Node parent = this.getRoot();
            for ( String name : paths) {
                Node child = parent.getChild(name);
                if (null == child) {
                    child = new Node(name);
                    parent.addChild(child);
                    fireEvent(TreeDataEvent.INTERVAL_ADDED, 
                            this.getPath(parent),
                            this.getIndexOfChild(parent, child),
                            parent.getChildCount() - 1,
                            this.getPath(child)
                            );
                }
                parent = child;
            }
            parent.setData(Pair.of(path, null != data
                                ? new String(data, Charsets.UTF_8)
                                : null));
        }

        @Override
        public void onUpdated(final ZKAgent agent, 
                final String absolutepath, 
                final byte[] data) throws Exception {
            if (!isManagedPath(absolutepath)) {
                return;
            }
            final String path = absolute2relative(absolutepath);
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
        public void onRemoved(final ZKAgent agent, 
                final String absolutepath) throws Exception {
            if (!isManagedPath(absolutepath)) {
                return;
            }
            final String path = absolute2relative(absolutepath);
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
            return 0 != this._client.checkExists().forPath(path).getEphemeralOwner();
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
        return this._client.create()
                .creatingParentsIfNeeded()
                .withMode(CreateMode.PERSISTENT)
                .forPath(path + "/" + desc.getName(), 
                        genBytes(desc.getParameters()));
    }

    private String absolute2relative(final String rawpath) {
        return rawpath;
    }

    private boolean isManagedPath(final String absolutepath) {
        return true;
    }

    private static byte[] genBytes(final String parameters) {
        if (null!=parameters) {
            return parameters.getBytes(Charsets.UTF_8);
        } else {
            return EMPTY_BYTES;
        }
    }
    
    private final CuratorFramework _client;
    private final ExecutorService _executor;
    private WebApp _webapp;
    private EventQueue<Event> _eventqueue;
    private final List<ZKAgent> _zkagents = Lists.newCopyOnWriteArrayList();
    
    //  TODO, replace with all source
    
    private final List<ModelSource> _models = new ArrayList<>();
}
