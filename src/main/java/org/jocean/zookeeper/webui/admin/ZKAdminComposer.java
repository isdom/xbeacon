package org.jocean.zookeeper.webui.admin;

import org.apache.commons.io.FilenameUtils;
import org.apache.curator.framework.CuratorFramework;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zkoss.zk.ui.event.Event;
import org.zkoss.zk.ui.event.EventListener;
import org.zkoss.zk.ui.event.Events;
import org.zkoss.zk.ui.select.SelectorComposer;
import org.zkoss.zk.ui.select.annotation.VariableResolver;
import org.zkoss.zk.ui.select.annotation.Wire;
import org.zkoss.zk.ui.select.annotation.WireVariable;
import org.zkoss.zul.Button;
import org.zkoss.zul.Menuitem;
import org.zkoss.zul.Textbox;
import org.zkoss.zul.Tree;
import org.zkoss.zul.Treecell;
import org.zkoss.zul.Treeitem;
import org.zkoss.zul.TreeitemRenderer;
import org.zkoss.zul.Treerow;
import org.zkoss.zul.Window;

import com.google.common.base.Charsets;

@VariableResolver(org.zkoss.zkplus.spring.DelegatingVariableResolver.class)
public class ZKAdminComposer extends SelectorComposer<Window>{
	
    private static final byte[] EMPTY_BYTES = new byte[0];

    /**
     * 
     */
    private static final long serialVersionUID = 3932475393168268600L;

    private static final Logger LOG = 
        	LoggerFactory.getLogger(ZKAdminComposer.class);
    
	public void doAfterCompose(final Window comp) throws Exception {
		super.doAfterCompose(comp);
		
		nodes.setItemRenderer(new NodeTreeRenderer());
		
		nodes.addEventListener(Events.ON_SELECT, new EventListener<Event>() {
			
			@Override
			public void onEvent(final Event event) throws Exception {
				final String fullpath = currentSelectedNode();
				if ( null != fullpath ) {
					LOG.info("select node:{}", fullpath);
					displayNodeData( fullpath );
				}
			}		
		});
		
		refreshNodes();
		
        addnode.addEventListener(Events.ON_CLICK, new EventListener<Event>() {

            @Override
            public void onEvent(final Event event) throws Exception {
                final String fullpath = currentSelectedNode();
                if ( null != fullpath ) {
                    LOG.info("try to add node for path:{}", fullpath);
                    addNodeFor(fullpath);
                }
            }});
        
        save.addEventListener(Events.ON_CLICK, new EventListener<Event>() {

            @Override
            public void onEvent(final Event event) throws Exception {
                final String fullpath = currentSelectedNode();
                if ( null != fullpath ) {
                    LOG.info("try to save current content for path:{}", fullpath);
                    saveCurrentContent(fullpath);
                }
            }});
	}

    private void refreshNodes() {
        nodes.setModel( new ZKTreeModel(this._zkclient, this._rootPath));
    }

	private void addNodeFor(final String fullpath) {
	    final Window dialog = new Window("Add Node", "normal", true);
	    dialog.setWidth("300px");
	    dialog.setHeight("100px");
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
                        final String nodepath = concatParentAndChild(fullpath, tbNodename.getText());
                        final String createdPath =_zkclient.create().forPath(nodepath, EMPTY_BYTES);
                        dialog.detach();
                        alert(createdPath + " created!");
                        refreshNodes();
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

    private void saveCurrentContent(final String fullpath) throws Exception {
	    final byte[] content = this.parameters.getText().getBytes(Charsets.UTF_8);
	    if (null!=content) {
	        this._zkclient.setData().forPath(fullpath, content);
	    }
    }

    private void displayNodeData(final String fullpath) throws Exception {
	    final byte[] data = this._zkclient.getData().forPath(fullpath);
	    if (null != data) {
	        final String content = new String(data, Charsets.UTF_8);
	        this.parameters.setText(content);
	    }
    }

   private String currentSelectedNode() {
        final Treeitem item = nodes.getSelectedItem();

        return null != item ? (String)item.getValue() : null;
    }

	
    private String concatParentAndChild(final String fullpath,
        final String child) {
    return fullpath + (!fullpath.endsWith("/") ? "/" : "") + child;
}


    class NodeTreeRenderer implements TreeitemRenderer<String> {
        public void render(final Treeitem item, final String fullname, int index) 
                throws Exception {
            item.setValue(fullname);
            item.appendChild( new Treerow() {
                private static final long serialVersionUID = 1L;
            {
                this.appendChild(
                    new Treecell(FilenameUtils.getName(fullname)));
            }});
        }
    }
    
    @Wire
    Tree        nodes;
	
    @Wire
    Textbox     parameters;
    
    @Wire
    Menuitem        addnode;
    
    @Wire
    Menuitem        save;
	
	@WireVariable("zkClient")
	private CuratorFramework _zkclient;
	
    @WireVariable("rootPath")
	private String _rootPath;
}
