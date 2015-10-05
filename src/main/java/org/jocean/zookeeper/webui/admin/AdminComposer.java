package org.jocean.zookeeper.webui.admin;

import java.util.List;

import org.apache.curator.framework.CuratorFramework;
import org.jocean.zkoss.model.SimpleTreeModel;
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
				if ( null != node ) {
					LOG.info("select node:{}", node.getData());
					displayNodeData( (String)node.getData() );
				}
			}		
		});
		
		refreshNodeTree();
		
        save.addEventListener(Events.ON_CLICK, new EventListener<Event>() {

            @Override
            public void onEvent(final Event event) throws Exception {
                final SimpleTreeModel.Node node = currentSelectedNode();
                if ( null != node ) {
                    LOG.info("try to save current content for path:{}", node.getData());
                    saveCurrentContent((String)node.getData());
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

    /**
	 * @throws Exception 
	 * 
	 */
	private void refreshNodeTree() throws Exception {
		nodes.setModel( new SimpleTreeModel(genTreeNode(null, _rootPath)) );
	}
	
   private SimpleTreeModel.Node currentSelectedNode() {
        final Treeitem item = nodes.getSelectedItem();

        final Object data = item.getValue();
        
        if ( data instanceof SimpleTreeModel.Node ) {
            return  (SimpleTreeModel.Node)data;
        }
        else {
            return  null;
        }
    }

	
    private SimpleTreeModel.Node genTreeNode(final String parentPath, final String nodeName) 
            throws Exception {
        final String fullPath = null != parentPath 
                ? parentPath + (!parentPath.endsWith("/") ? "/" : "") + nodeName
                : nodeName;
        final SimpleTreeModel.Node node = new SimpleTreeModel.Node(nodeName);
        
        node.setData(fullPath);
        final List<String> children = _zkClient.getChildren().forPath(fullPath);
        for (String child : children) {
            node.addChild(genTreeNode(fullPath, child));
        }
        
        return node;
    }

    class NodeTreeRenderer implements TreeitemRenderer<Object> {
        public void render(final Treeitem item, final Object data, int index) 
                throws Exception {
            item.setValue(data);
            item.appendChild( new Treerow() {
                private static final long serialVersionUID = 1L;
            {
                if ( data instanceof SimpleTreeModel.Node) {
                    this.appendChild(
                        new Treecell(((SimpleTreeModel.Node)data).getName()));
                }
//                else if ( data instanceof ServiceDescription ) {
//                    final ServiceDescription desc = (ServiceDescription)data;
//                    this.appendChild(
//                        new Treecell( desc.getName() ) {
//                            private static final long serialVersionUID = 1L;
//                        {
//                            this.setImage("server.png");
//                            this.setTooltiptext(desc.getJmxurl());
//                        }} );
//                    
//                }
                else {
                    LOG.warn("unknown tree node {}, just ignore", data);
                }
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
	
//    private     TreeCache _treeCache;
    
	@WireVariable("zkClient")
	private CuratorFramework _zkClient;
	
    @WireVariable("rootPath")
	private String _rootPath;
}
