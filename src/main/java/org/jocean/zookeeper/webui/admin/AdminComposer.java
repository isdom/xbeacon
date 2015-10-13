package org.jocean.zookeeper.webui.admin;

import java.util.HashMap;
import java.util.Map;

import org.jocean.idiom.ExceptionUtils;
import org.jocean.zkoss.model.SimpleTreeModel;
import org.jocean.zkoss.model.SimpleTreeModel.Node;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zkoss.zk.ui.event.Event;
import org.zkoss.zk.ui.event.EventListener;
import org.zkoss.zk.ui.event.EventQueues;
import org.zkoss.zk.ui.event.Events;
import org.zkoss.zk.ui.event.InputEvent;
import org.zkoss.zk.ui.event.SelectEvent;
import org.zkoss.zk.ui.select.SelectorComposer;
import org.zkoss.zk.ui.select.annotation.VariableResolver;
import org.zkoss.zk.ui.select.annotation.Wire;
import org.zkoss.zk.ui.select.annotation.WireVariable;
import org.zkoss.zul.Button;
import org.zkoss.zul.Caption;
import org.zkoss.zul.Menuitem;
import org.zkoss.zul.Messagebox;
import org.zkoss.zul.Tab;
import org.zkoss.zul.Tabpanel;
import org.zkoss.zul.Tabpanels;
import org.zkoss.zul.Tabs;
import org.zkoss.zul.Textbox;
import org.zkoss.zul.Tree;
import org.zkoss.zul.Treecell;
import org.zkoss.zul.Treeitem;
import org.zkoss.zul.TreeitemRenderer;
import org.zkoss.zul.Treerow;
import org.zkoss.zul.Window;

import com.google.common.base.Charsets;

@VariableResolver(org.zkoss.zkplus.spring.DelegatingVariableResolver.class)
public class AdminComposer extends SelectorComposer<Window>{
	
    /**
     * 
     */
    private static final long serialVersionUID = -9063622647244777848L;

    private static final Logger LOG = 
        	LoggerFactory.getLogger(AdminComposer.class);
    
	public void doAfterCompose(final Window comp) throws Exception {
		super.doAfterCompose(comp);
		
		nodes.setItemRenderer(new NodeTreeRenderer());
		
		nodes.addEventListener(Events.ON_SELECT, new EventListener<Event>() {
			
			@Override
			public void onEvent(final Event event) throws Exception {
				final SimpleTreeModel.Node node = currentSelectedNode();
				configNodesMenus(null!=node);
				if ( null != node ) {
					LOG.info("select node:{}", node.getData());
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
        
        save.addEventListener(Events.ON_CLICK, new EventListener<Event>() {

            @Override
            public void onEvent(final Event event) throws Exception {
                final SimpleTreeModel.Node node = currentSelectedNode();
                if ( null != node ) {
                    LOG.info("try to save current content for path:{}", node.getData());
                    saveCurrentContent(node);
                }
            }});
        
        EventQueues.lookup("zktree", EventQueues.APPLICATION, true)
        .subscribe(new EventListener<Event>() {
            @Override
            public void onEvent(final Event event) throws Exception {
                refreshNodeTree();
            }});
        
        enableSaveOperation(false);
        refreshNodeTree();
	}

    private void configNodesMenus(final boolean nodeSelected) {
        addnode.setDisabled(!nodeSelected);
        delnode.setDisabled(!nodeSelected);
    }

    private void addNodeFor(final Node node) {
        final String path = _zka.getNodePath(node);
        final Window dialog = new Window("Add Node for [" + path + "]", "normal", true);
        dialog.setWidth("300px");
        dialog.setHeight("550px");
        dialog.setSizable(false);
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
                        final String createdPath = _zka.createZKNode(nodepath, nodecontent);
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
        final String path = _zka.getNodePath(node);
        Messagebox.show("Are you sure to delete node(" + path + ")?", 
            "Confirm Dialog", 
            Messagebox.OK | Messagebox.CANCEL, 
            Messagebox.QUESTION, 
            new org.zkoss.zk.ui.event.EventListener<Event>() {
                public void onEvent(Event evt) throws InterruptedException {
                    if (evt.getName().equals("onOK")) {
                        try {
                            _zka.removeZKNode(path);
                            alert(path + " deleted!");
                        } catch (Exception e) {
                            alert(ExceptionUtils.exception2detail(e));
                        }
                    } else {
                    }
            }});
    }
    
	private void saveCurrentContent(final Node node) throws Exception {
	    if (null!=this._currentContent) {
	        this._currentContent.saveNodeContent();
	    }
    }

    private void displayNodeData(final Node node) throws Exception {
        final String path = this._zka.getNodePath(node);
        final NodeContent content = this._contents.get(path);
        
        if (null != content ) {
            content._tab.setSelected(true);
            enableSaveOperation(content._isModified);
            this._currentContent = content;
        } else {
            final NodeContent newcontent = new NodeContent(node);
            final Tab newtab;
            final Tabpanel newtabpanel;
            final Textbox newtextbox = new Textbox();
            newtextbox.setWidth("100%");
            newtextbox.setHeight("100%");
            newtextbox.setMultiline(true);
            maintabs.appendChild(newtab = new Tab(path) {
                void doClose() {
                    super.close();
                    _contents.remove(path);
                }
                
                /* (non-Javadoc)
                 * @see org.zkoss.zul.Tab#close()
                 */
                @Override
                public void close() {
                    if (newcontent.isModified()) {
                        Messagebox.show("Content has modified, Are you sure to discard?", "Confirm Dialog", 
                                Messagebox.OK | Messagebox.CANCEL, 
                                Messagebox.QUESTION, 
                                new EventListener<Event>() {
                            public void onEvent(Event evt) throws InterruptedException {
                                if (evt.getName().equals("onOK")) {
                                    doClose();
                                }
                            }});
                    } else {
                        doClose();
                    }
                }
                private static final long serialVersionUID = 1L;{
                    this.setClosable(true);
                }});
            maintabpanels.appendChild(newtabpanel = new Tabpanel() {
                private static final long serialVersionUID = 1L; {
                    this.appendChild(newtextbox);
                }});
            newtextbox.setText(this._zka.getNodeDataAsString(node));
            newcontent.fill(newtab, newtabpanel, newtextbox);
            this._contents.put(path, newcontent);
            newtextbox.addEventListener(Events.ON_CHANGING, new EventListener<InputEvent>() {
                @Override
                public void onEvent(final InputEvent event) throws Exception {
                    newcontent.markModified();
                    enableSaveOperation(true);
                }});
            newtab.setSelected(true);
            enableSaveOperation(false);
            newtab.addEventListener(Events.ON_SELECT, new EventListener<SelectEvent<Tab, Object>>() {
                @Override
                public void onEvent(final SelectEvent<Tab, Object> event) throws Exception {
                    enableSaveOperation(newcontent._isModified);
                    status.setLabel(path);
                }});
            this._currentContent = newcontent;
        }
    }

    /**
	 * @throws Exception 
	 * 
	 */
	private void refreshNodeTree() throws Exception {
		nodes.setModel( _zka.getModel() );
		configNodesMenus(false);
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

    private void enableSaveOperation(final boolean enabled) {
        save.setDisabled(!enabled);
    }

    class NodeTreeRenderer implements TreeitemRenderer<SimpleTreeModel.Node> {
        public void render(final Treeitem item, final SimpleTreeModel.Node node, int index) 
                throws Exception {
            item.setValue(node);
            item.appendChild( new Treerow() {
                private static final long serialVersionUID = 1L;
            {
                this.appendChild(
                    new Treecell(node.getName()));
            }});
        }
    }
    
    @Wire
    private Tree    nodes;
	
    @Wire
    private Tabs    maintabs;
    
    @Wire
    private Tabpanels    maintabpanels;
    
    @Wire
    private Menuitem        addnode;
    
    @Wire
    private Menuitem        delnode;
    
    @Wire
    private Menuitem        save;
    
    @Wire
    private Caption         status;
    
    private class NodeContent {
        private final Node _node;
        private Tab _tab;
        private Tabpanel _tabpanel;
        private Textbox _textbox;
        private boolean _isModified = false;
        
        NodeContent(final Node node) {
            this._node = node;
        }
        
        void fill(final Tab tab, final Tabpanel tabpanel, final Textbox textbox) {
            this._tab = tab;
            this._tabpanel = tabpanel;
            this._textbox = textbox;
        }

        boolean isModified() {
            return this._isModified;
        }
        
        void markModified() {
            if (!this._isModified) {
                this._isModified = true;
                this._tab.setLabel(_zka.getNodePath(this._node) + " *");
            }
        }

        void saveNodeContent() throws Exception{
            _zka.setNodeDataAsString( this._node,  this._textbox.getText());
            this._isModified = false;
            this._tab.setLabel(_zka.getNodePath(this._node));
        }
    }
    
    private NodeContent _currentContent;
    
    private final Map<String, NodeContent>  _contents = new HashMap<>();
    
	@WireVariable("zkagent")
	private ZKAgent _zka;
	
    @WireVariable("rootPath")
    private String _rootPath;
}
