package org.jocean.xbeacon.zk;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import org.jocean.idiom.ExceptionUtils;
import org.jocean.idiom.Pair;
import org.jocean.j2se.unit.model.UnitDescription;
import org.jocean.zkoss.model.SimpleTreeModel;
import org.jocean.zkoss.model.SimpleTreeModel.Node;
import org.jocean.zkoss.ui.EditableTab;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;
import org.zkoss.zk.ui.event.Event;
import org.zkoss.zk.ui.event.EventListener;
import org.zkoss.zk.ui.event.Events;
import org.zkoss.zk.ui.event.InputEvent;
import org.zkoss.zk.ui.select.SelectorComposer;
import org.zkoss.zk.ui.select.annotation.VariableResolver;
import org.zkoss.zk.ui.select.annotation.Wire;
import org.zkoss.zk.ui.select.annotation.WireVariable;
import org.zkoss.zul.Button;
import org.zkoss.zul.Caption;
import org.zkoss.zul.Menuitem;
import org.zkoss.zul.Messagebox;
import org.zkoss.zul.Tabpanels;
import org.zkoss.zul.Tabs;
import org.zkoss.zul.Textbox;
import org.zkoss.zul.Tree;
import org.zkoss.zul.TreeModel;
import org.zkoss.zul.Treecell;
import org.zkoss.zul.Treeitem;
import org.zkoss.zul.TreeitemRenderer;
import org.zkoss.zul.Treerow;
import org.zkoss.zul.Window;
import org.zkoss.zul.event.TreeDataEvent;
import org.zkoss.zul.event.TreeDataListener;

import com.google.common.base.Charsets;

import rx.functions.Action0;
import rx.functions.Action1;

@VariableResolver(org.zkoss.zkplus.spring.DelegatingVariableResolver.class)
public class ZooKeeperComposer extends SelectorComposer<Window>{
	
    private static final long serialVersionUID = -9063622647244777848L;

    private static final Logger LOG = 
        	LoggerFactory.getLogger(ZooKeeperComposer.class);
    
	public void doAfterCompose(final Window comp) throws Exception {
		super.doAfterCompose(comp);
		
		nodes.setItemRenderer(new NodeTreeRenderer());
		
		nodes.addEventListener(Events.ON_SELECT, new EventListener<Event>() {
			
			@Override
			public void onEvent(final Event event) throws Exception {
				final SimpleTreeModel.Node node = currentSelectedNode();
				if ( null != node ) {
					LOG.info("select node:{}", (Object)node.getData());
					displayNodeData(node);
				}
			}		
		});
		
        addnode.addEventListener(Events.ON_CLICK, new EventListener<Event>() {
            @Override
            public void onEvent(final Event event) throws Exception {
                final SimpleTreeModel.Node node = currentSelectedNode();
                if ( null != node ) {
                    LOG.info("try to add node for path:{}", node);
                    addNodeFor(node);
                }
            }});
        
        delnode.addEventListener(Events.ON_CLICK, new EventListener<Event>() {
            @Override
            public void onEvent(final Event event) throws Exception {
                final SimpleTreeModel.Node node = currentSelectedNode();
                if ( null != node ) {
                    LOG.info("try to del node:{}", node);
                    delNodeFor(node);
                }
            }});
        
        backup.addEventListener(Events.ON_CLICK, new EventListener<Event>() {
            @Override
            public void onEvent(final Event event) throws Exception {
                final SimpleTreeModel.Node node = currentSelectedNode();
                if ( null != node ) {
                    LOG.info("try to backup from sub-tree :{}", node);
                    backupNode(node);
                }
            }});
        
        restore.addEventListener(Events.ON_CLICK, new EventListener<Event>() {
            @Override
            public void onEvent(final Event event) throws Exception {
                final SimpleTreeModel.Node node = currentSelectedNode();
                if ( null != node ) {
                    LOG.info("try to restore sub-tree from :{}", node);
                    restoreFromNode(node);
                }
            }});
        
        closeall.addEventListener(Events.ON_CLICK, new EventListener<Event>() {
            @Override
            public void onEvent(final Event event) throws Exception {
                while (!_tabs.values().isEmpty()) {
                    _tabs.values().iterator().next().second.close();
                }
            }});
        
        refreshNodeTree();
	}

    private void onZKNodeRemoved(final String path) {
        final Pair<Textbox,EditableTab> pair = this._tabs.get(path);
        if (null!=pair) {
            pair.second.close();
        }
    }

    private void restoreFromNode(final Node node) {
        final String path = _zkmgr.getNodePath(node);
        final Window dialog = new Window("Restore From Node [" + path + "], to ...", "normal", true);
        dialog.setWidth("300px");
        dialog.setHeight("200px");
        dialog.setSizable(false);
        dialog.setPage(this.getPage());
        
        final Textbox tbNodename = new Textbox() {
            private static final long serialVersionUID = 1L; 
            {
                this.setWidth("260px");
            }};
        final Button btnOK = new Button("OK") {
            private static final long serialVersionUID = 1L;
            {
                this.addEventListener(Events.ON_CLICK, new EventListener<Event>() {
                    @Override
                    public void onEvent(Event event) throws Exception {
                        final String createdPath = doRestoreTo(node, tbNodename.getText());
                        dialog.detach();
                        if (null!=createdPath) {
                            alert(path + " restore to " + createdPath + " succeed!");
                        } else {
                            alert(path + " restore failed!");
                        }
                    }});
            }
            };
        final Button btnCancel = new Button("Cancel") {
            private static final long serialVersionUID = 1L;
            {
                this.addEventListener(Events.ON_CLICK, new EventListener<Event>() {
                    @Override
                    public void onEvent(Event event) throws Exception {
                        dialog.detach();
                    }});
            }
            };
        dialog.appendChild(tbNodename);
        dialog.appendChild(btnOK);
        dialog.appendChild(btnCancel);
        dialog.doModal();
    }
    
    private String doRestoreTo(final Node node, final String restoreTo) {
        final Yaml yaml = new Yaml(new Constructor(UnitDescription.class));
        final UnitDescription root = (UnitDescription)yaml.load(this._zkmgr.getNodeDataAsString(node));
        try {
            return this._zkmgr.importNode(restoreTo, root);
        } catch (Exception e) {
            LOG.warn("exception when createZKNode for path {}, detail: {}",
                    restoreTo, ExceptionUtils.exception2detail(e));
        }
        
        return null;
    }

    private void backupNode(final Node node) {
        final String path = _zkmgr.getNodePath(node);
        final Window dialog = new Window("Backup Node for [" + path + "], to ...", "normal", true);
        dialog.setWidth("300px");
        dialog.setHeight("200px");
        dialog.setSizable(false);
        dialog.setPage(this.getPage());
        
        final Textbox tbNodename = new Textbox() {
            private static final long serialVersionUID = 1L; 
            {
                this.setWidth("260px");
            }};
        final Button btnOK = new Button("OK") {
            private static final long serialVersionUID = 1L;
            {
                this.addEventListener(Events.ON_CLICK, new EventListener<Event>() {
                    @Override
                    public void onEvent(Event event) throws Exception {
                        final String createdPath = doBackupFor(node, tbNodename.getText());
                        dialog.detach();
                        if (null!=createdPath) {
                            alert(path + " backup to " + createdPath + " succeed!");
                        } else {
                            alert(path + " backup failed!");
                        }
                    }});
            }
            };
        final Button btnCancel = new Button("Cancel") {
            private static final long serialVersionUID = 1L;
            {
                this.addEventListener(Events.ON_CLICK, new EventListener<Event>() {
                    @Override
                    public void onEvent(Event event) throws Exception {
                        dialog.detach();
                    }});
            }
            };
        dialog.appendChild(tbNodename);
        dialog.appendChild(btnOK);
        dialog.appendChild(btnCancel);
        dialog.doModal();
    }

    private String doBackupFor(final Node node, final String backupTo) {
        
        final UnitDescription root = this._zkmgr.node2desc(node);
        if (null!=root) {
            final Yaml yaml = new Yaml(new Constructor(UnitDescription.class));
            final String backupcontent = yaml.dump(root);
            
            try {
                return _zkmgr.createZKNode(backupTo, backupcontent.getBytes(Charsets.UTF_8));
            } catch (Exception e) {
                LOG.warn("exception when createZKNode for path {}, detail: {}",
                        backupTo, ExceptionUtils.exception2detail(e));
            }
        }
        
        return null;
    }
    
    private void enableNodesMenus(final boolean enabled) {
        this.addnode.setDisabled(!enabled);
        this.delnode.setDisabled(!enabled);
        this.backup.setDisabled(!enabled);
        this.restore.setDisabled(!enabled);
    }

    private void addNodeFor(final Node node) {
        final String path = _zkmgr.getNodePath(node);
        final Window dialog = new Window("Add Node for [" + path + "]", "normal", true);
        dialog.setWidth("300px");
        dialog.setHeight("550px");
        dialog.setSizable(true);
        dialog.setPage(this.getPage());
        
        final Textbox tbNodename = new Textbox() {
            private static final long serialVersionUID = 1L; 
            {
                this.setWidth("260px");
            }};
        final Textbox tbNodecontent = new Textbox() {
            private static final long serialVersionUID = 1L; 
            {
                this.setWidth("260px");
                this.setHeight("400px");
                this.setMultiline(true);
            }};
        final Button btnOK = new Button("OK") {
            private static final long serialVersionUID = 1L;
            {
                this.addEventListener(Events.ON_CLICK, new EventListener<Event>() {
                    @Override
                    public void onEvent(Event event) throws Exception {
                        final String nodepath = concatParentAndChild(path, tbNodename.getText());
                        final byte[] nodecontent = tbNodecontent.getText().getBytes(Charsets.UTF_8);
                        final String createdPath = _zkmgr.createZKNode(nodepath, nodecontent);
                        dialog.detach();
                        alert(createdPath + " created!");
                    }});
            }
            };
        final Button btnCancel = new Button("Cancel") {
            private static final long serialVersionUID = 1L;
            {
                this.addEventListener(Events.ON_CLICK, new EventListener<Event>() {
                    @Override
                    public void onEvent(Event event) throws Exception {
                        dialog.detach();
                    }});
            }
            };
        dialog.appendChild(tbNodename);
        dialog.appendChild(tbNodecontent);
        dialog.appendChild(btnOK);
        dialog.appendChild(btnCancel);
        dialog.doModal();
    }

    private void delNodeFor(final Node node) {
        final String path = _zkmgr.getNodePath(node);
        Messagebox.show("Are you sure to delete node(" + path + ")?", 
            "Confirm Dialog", 
            Messagebox.OK | Messagebox.CANCEL, 
            Messagebox.QUESTION, 
            new org.zkoss.zk.ui.event.EventListener<Event>() {
                public void onEvent(Event evt) throws InterruptedException {
                    if (evt.getName().equals("onOK")) {
                        try {
                            _zkmgr.removeZKNode(path);
                            alert(path + " deleted!");
                        } catch (Exception e) {
                            alert(ExceptionUtils.exception2detail(e));
                        }
                    } else {
                    }
            }});
    }
    
    private void displayNodeData(final Node node) {
        final String treepath = Arrays.toString(this._model.getPath(node));
        final String path = this._zkmgr.getNodePath(node);
        final Pair<Textbox,EditableTab> pair = this._tabs.get(treepath);
        
        if (null != pair ) {
            pair.second.setSelected();
        } else {
            this._tabs.put(treepath, buildNewTab(node, path, treepath));
            
        }
    }

    private Pair<Textbox,EditableTab> buildNewTab(final Node node, final String path, final String treepath) {
        final Textbox textbox = new Textbox();
        textbox.setDisabled(true);
        textbox.setStyle("font-family:Courier New");
        textbox.setWidth("100%");
        textbox.setHeight("90%");
        textbox.setMultiline(true);
        textbox.setText( this._zkmgr.getNodeDataAsString(node));
        final EditableTab tab = new EditableTab(path)
                .setOnClose(new Action0() {
                    @Override
                    public void call() {
                        _tabs.remove(treepath);
                    }})
            .setOnApply(new Action0() {
                @Override
                public void call() {
                    try {
                        _zkmgr.setNodeDataAsString(node,  textbox.getText());
                    } catch (Exception e) {
                        LOG.warn("exception when save data for {}, detail:{}",
                                path, ExceptionUtils.exception2detail(e));
                    }
                }})
            .setOnEnableEdit(new Action1<Boolean> () {
                @Override
                public void call(final Boolean editEnabled) {
                    textbox.setDisabled(!editEnabled);
                }})
            .appendChild(textbox)
            .appendToTabs(this.maintabs)
            .appendToTabpanels(this.maintabpanels);
        
        textbox.addEventListener(Events.ON_CHANGING, new EventListener<InputEvent>() {
            @Override
            public void onEvent(final InputEvent event) throws Exception {
                tab.markModified();
            }});
        return Pair.of(textbox,tab);
    }

	private void refreshNodeTree() throws Exception {
	    this._model = this._zkmgr.getModel().model();
		this.nodes.setModel(_model);
		enableNodesMenus(false);
		this._model.addTreeDataListener(new TreeDataListener() {
            @Override
            public void onChange(final TreeDataEvent event) {
                if (event.getType() == TreeDataEvent.CONTENTS_CHANGED) {
                    onContentChanged(event);
                } else if (event.getType() == TreeDataEvent.SELECTION_CHANGED) {
                    enableNodesMenus(null!=currentSelectedNode());
                } else if (event.getType() == TreeDataEvent.INTERVAL_REMOVED) {
                    enableNodesMenus(false);
                    onZKNodeRemoved(Arrays.toString(event.getAffectedPath()));
                }
            }});
	}
	
    private void onContentChanged(final TreeDataEvent event) {
        final String treepath = Arrays.toString(event.getAffectedPath());
        final Pair<Textbox,EditableTab> pair = this._tabs.get(treepath);
        if (null!=pair) {
            final SimpleTreeModel.Node node = 
                    (SimpleTreeModel.Node)event.getModel().getChild(event.getAffectedPath());
            pair.first.setText(_zkmgr.getNodeDataAsString(node));
        }
    }

    private String concatParentAndChild(final String fullpath,
            final String child) {
        return fullpath + (!fullpath.endsWith("/") ? "/" : "") + child;
    }
    
   private SimpleTreeModel.Node currentSelectedNode() {
        final Treeitem item = nodes.getSelectedItem();

        if (null!=item) {
            final Object data = item.getValue();
        
            if ( data instanceof SimpleTreeModel.Node ) {
                return  (SimpleTreeModel.Node)data;
            }
        }
        return  null;
    }

    class NodeTreeRenderer implements TreeitemRenderer<SimpleTreeModel.Node> {
        public void render(final Treeitem item, final SimpleTreeModel.Node node, int index) 
                throws Exception {
            item.setValue(node);
            item.appendChild( new Treerow() {
                private static final long serialVersionUID = 1L;
            {
                this.appendChild(new Treecell(node.getName()));
            }});
        }
    }
    
    @Wire
    private Tree    nodes;
    
    private TreeModel<SimpleTreeModel.Node> _model;
	
    @Wire
    private Tabs    maintabs;
    
    @Wire
    private Tabpanels    maintabpanels;
    
    @Wire
    private Menuitem        addnode;
    
    @Wire
    private Menuitem        delnode;
    
    @Wire
    private Menuitem        backup;
    
    @Wire
    private Menuitem        restore;
    
    @Wire
    private Menuitem        closeall;
    
    @Wire
    private Caption         status;
    
	@WireVariable("treemgr") 
	private ZKTreeManager _zkmgr;
	
    private final Map<String, Pair<Textbox,EditableTab>>  _tabs = new HashMap<>();
}
