package org.jocean.zookeeper.webui.admin;

import org.apache.curator.framework.CuratorFramework;
import org.zkoss.zul.AbstractTreeModel;

public class ZKTreeModel extends AbstractTreeModel<String> {
	

	/**
     * 
     */
    private static final long serialVersionUID = 3988274871829860157L;
    
	public ZKTreeModel(final CuratorFramework zkclient, final String rootPath) {
		super(rootPath);
		this._zkclient = zkclient;
	}

	@Override
	public boolean isLeaf(final String node) {
		return getChildCount(node)==0;
	}

	@Override
	public String getChild(final String parent, int index) {
        try {
            return parent + (!parent.endsWith("/") ? "/" : "") 
                    + this._zkclient.getChildren().forPath(parent).get(index);
        } catch (Exception e) {
            return null;
        }
	}

	@Override
	public int getChildCount(final String parent) {
	    try {
            return this._zkclient.getChildren().forPath(parent).size();
        } catch (Exception e) {
            return 0;
        }
	}
	
	private final CuratorFramework _zkclient;
}
