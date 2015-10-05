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
		
		nodes.setModel( new ZKTreeModel(this._zkClient, this._rootPath));
		
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

	private void saveCurrentContent(final String fullpath) throws Exception {
	    final byte[] content = this.parameters.getText().getBytes(Charsets.UTF_8);
	    if (null!=content) {
	        this._zkClient.setData().forPath(fullpath, content);
	    }
    }

    private void displayNodeData(final String fullpath) throws Exception {
	    final byte[] data = this._zkClient.getData().forPath(fullpath);
	    if (null != data) {
	        final String content = new String(data, Charsets.UTF_8);
	        this.parameters.setText(content);
	    }
    }

   private String currentSelectedNode() {
        final Treeitem item = nodes.getSelectedItem();

        return (String)item.getValue();
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
    Menuitem        save;
//    @Wire
//    Tabs        maintabs;
//	
//    @Wire
//    Tabpanels   maintabpanels;
	
	@WireVariable("zkClient")
	private CuratorFramework _zkClient;
	
    @WireVariable("rootPath")
	private String _rootPath;
}
