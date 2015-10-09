package org.jocean.zookeeper.webui.admin;

import org.jocean.zkoss.model.SimpleTreeModel;
import org.jocean.zkoss.model.SimpleTreeModel.Node;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zkoss.zk.ui.event.Event;
import org.zkoss.zk.ui.event.EventListener;
import org.zkoss.zk.ui.event.EventQueues;
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
					displayNodeData(node);
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
                    saveCurrentContent(node);
                }
            }});
        EventQueues.lookup("zktree", EventQueues.APPLICATION, true)
        .subscribe(new EventListener<Event>() {
            @Override
            public void onEvent(final Event event) throws Exception {
                refreshNodeTree();
            }});
	}

	private void saveCurrentContent(final Node node) throws Exception {
	    this._zka.setNodeDataAsString(node,  this.parameters.getText());
    }
	
    private void displayNodeData(final Node node) throws Exception {
        this.parameters.setText(this._zka.getNodeDataAsString(node));
    }

    /**
	 * @throws Exception 
	 * 
	 */
	private void refreshNodeTree() throws Exception {
		nodes.setModel( _zka.getModel() );
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

    class NodeTreeRenderer implements TreeitemRenderer<SimpleTreeModel.Node> {
        public void render(final Treeitem item, final SimpleTreeModel.Node data, int index) 
                throws Exception {
            item.setValue(data);
            item.appendChild( new Treerow() {
                private static final long serialVersionUID = 1L;
            {
                this.appendChild(
                    new Treecell(((SimpleTreeModel.Node)data).getName()));
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
    Menuitem        delnode;
    
    @Wire
    Menuitem        save;
    
	@WireVariable("zkagent")
	private ZKAgent _zka;
	
    @WireVariable("rootPath")
    private String _rootPath;
}
