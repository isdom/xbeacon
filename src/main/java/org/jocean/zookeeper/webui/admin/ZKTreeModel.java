package org.jocean.zookeeper.webui.admin;

import org.apache.curator.framework.CuratorFramework;
import org.zkoss.zul.AbstractTreeModel;

public class ZKTreeModel extends AbstractTreeModel<String> {
	

    private static final String _ROOT = "<ROOT>";
    
	/**
     * 
     */
    private static final long serialVersionUID = 3988274871829860157L;
    
	public ZKTreeModel(final CuratorFramework zkclient, final String rootPath) {
		super(_ROOT);
		this._rootPath = rootPath;
		this._zkclient = zkclient;
	}

	@Override
	public boolean isLeaf(final String node) {
		return getChildCount(node)==0;
	}

	@Override
	public String getChild(final String parent, int index) {
        if (_ROOT == parent) {
            return this._rootPath;
        } else {
            try {
                return parent + (!parent.endsWith("/") ? "/" : "") 
                        + this._zkclient.getChildren().forPath(parent).get(index);
            } catch (Exception e) {
                return null;
            }
        }
	}

	@Override
	public int getChildCount(final String parent) {
	    if (_ROOT == parent) {
	        return 1;
	    } else {
    	    try {
                return this._zkclient.getChildren().forPath(parent).size();
            } catch (Exception e) {
                return 0;
            }
	    }
	}
	
	private final CuratorFramework _zkclient;
	private final String _rootPath;
}
