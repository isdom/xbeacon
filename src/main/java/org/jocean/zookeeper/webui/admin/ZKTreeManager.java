package org.jocean.zookeeper.webui.admin;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

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
import org.zkoss.zk.ui.Desktop;
import org.zkoss.zk.ui.Executions;
import org.zkoss.zk.ui.WebApp;
import org.zkoss.zk.ui.event.Event;
import org.zkoss.zk.ui.event.EventListener;
import org.zkoss.zk.ui.event.EventQueue;
import org.zkoss.zk.ui.event.EventQueues;
import org.zkoss.zk.ui.util.DesktopCleanup;
import org.zkoss.zul.event.TreeDataEvent;

import com.google.common.base.Charsets;

import rx.functions.Action2;

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
    }
    
    public SimpleTreeModel getModel() throws Exception {
        final ZKTreeModel model = new ZKTreeModel(new SimpleTreeModel.Node(this._rootPath));
        final UUID myid = UUID.randomUUID();
        
        this._eventqueue.subscribe(new EventListener<Event>() {
            @SuppressWarnings("unchecked")
            @Override
            public void onEvent(final Event event) throws Exception {
                if ( event.getName().equals(EVENT_ZK_CHANGED)) {
                    ((Action2<UUID, ZKAgent.Listener>)event.getData()).call(myid, model);
                }
            }});

        final Runnable stop = this._zkagent.addListener(new ZKAgent.Listener() {
            @Override
            public void onAdded(final String path, final byte[] data)
                    throws Exception {
                _eventqueue.publish(new Event(EVENT_ZK_CHANGED, null, new Action2<UUID, ZKAgent.Listener>() {
                    @Override
                    public void call(final UUID id, final Listener listener) {
                        if (id.equals(myid)) {
                            try {
                                listener.onAdded(path, data);
                            } catch (Exception e) {
                                LOG.warn("exception when onAdded for path {}, detail: {}",
                                        path, ExceptionUtils.exception2detail(e));
                            }
                        } else {
                            LOG.info("id {} NOT equals {}, just ignore.", id, myid);
                        }
                    }}));
            }

            @Override
            public void onUpdated(final String path, final byte[] data)
                    throws Exception {
                _eventqueue.publish(new Event(EVENT_ZK_CHANGED, null, new Action2<UUID, ZKAgent.Listener>() {
                    @Override
                    public void call(final UUID id, final Listener listener) {
                        if (id.equals(myid)) {
                            try {
                                listener.onUpdated(path, data);
                            } catch (Exception e) {
                                LOG.warn("exception when onUpdated for path {}, detail: {}",
                                        path, ExceptionUtils.exception2detail(e));
                            }
                        }
                    }}));
            }

            @Override
            public void onRemoved(final String path) throws Exception {
                _eventqueue.publish(new Event(EVENT_ZK_CHANGED, null, new Action2<UUID, ZKAgent.Listener>() {
                    @Override
                    public void call(final UUID id, final Listener listener) {
                        if (id.equals(myid)) {
                            try {
                                listener.onRemoved(path);
                            } catch (Exception e) {
                                LOG.warn("exception when onRemoved for path {}, detail: {}",
                                        path, ExceptionUtils.exception2detail(e));
                            }
                        }
                    }}));
            }});
        final Desktop desktop = Executions.getCurrent().getDesktop();
        desktop.addListener(new DesktopCleanup() {
            @Override
            public void cleanup(final Desktop desktop) throws Exception {
                LOG.info("cleanup for desktop {}", desktop);
                stop.run();
            }});
        return model;
    }
    
    /**
     * @param _webapp the _webapp to set
     */
    public void setWebapp(final WebApp webapp) {
        this._webapp = webapp;
    }

    public void setRoot(final String rootPath) {
    }
    
    public void start() throws Exception {
        this._eventqueue = EventQueues.lookup("zktree", this._webapp, true);
    }
    
    public void stop() {
        EventQueues.remove("zktree", this._webapp);
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
        
        @Override
        public void onAdded(final String absolutepath, final byte[] data) throws Exception {
            final String path = absolute2relative(absolutepath);
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
        public void onUpdated(final String absolutepath, final byte[] data) throws Exception {
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
        public void onRemoved(final String absolutepath) throws Exception {
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

    private String absolute2relative(final String rawpath) {
        return rawpath.substring(_rootPath.length());
    }

    private static byte[] genBytes(final String parameters) {
        if (null!=parameters) {
            return parameters.getBytes(Charsets.UTF_8);
        } else {
            return EMPTY_BYTES;
        }
    }
    
    private ZKAgent _zkagent;
    private WebApp _webapp;
    private String _rootPath;
    private EventQueue<Event> _eventqueue;
}