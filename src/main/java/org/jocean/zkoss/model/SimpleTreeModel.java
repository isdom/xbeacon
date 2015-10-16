package org.jocean.zkoss.model;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.zkoss.zul.AbstractTreeModel;

public class SimpleTreeModel extends AbstractTreeModel<Object> {
	
	/**
	 * 
	 */
	private static final long serialVersionUID = -6720368595112891822L;

	static public class Node {
		
		public Node(final String name) {
			this._name = name;
		}
		
		public Node addChildIfAbsent(final String nameForAdd) {
			for ( Object child : this._children ) {
				if ( child instanceof Node ) {
					if ( ((Node)child)._name.equals(nameForAdd) ) {
						return	(Node)child;
					}
				}
			}
			
			final Node toAdd = new Node(nameForAdd);
			this._children.add(toAdd);
			return	toAdd;
		}
		
		public Node addChildrenIfAbsent(final String[] path) {
			Node parent = this;
			for ( String name : path) {
				parent = parent.addChildIfAbsent(name);
			}
			
			return	parent;
		}
		
		public Node addChild(final Object child) {
			this._children.add(child);
			return	this;
		}

		public <T> Node addChildren(final Collection<T> children) {
			this._children.addAll(children);
			return	this;
		}
		
        public <T> Node removeChild(final String[] path) {
            Node parent = null, child = this;
            for (String name : path) {
                parent = child;
                child = parent.getChild(name);
                if (null == child) {
                    return null;
                }
            }
            
            parent.removeChild(child);
            
            return  child;
        }
        
        public String getName() {
			return	this._name;
		}
		
        public Node getChild(final String name) {
            for ( Object child : this._children ) {
                if ( child instanceof Node ) {
                    if ( ((Node)child)._name.equals(name) ) {
                        return  (Node)child;
                    }
                }
            }
            
            return null;
        }
        
        public Node getDescendant(final String[] path) {
            Node parent = this;
            for (String name : path) {
                parent = parent.getChild(name);
                if (null == parent) {
                    return null;
                }
            }
            
            return  parent;
        }
        
		@SuppressWarnings("unchecked")
        public <T> T getChild(final int idx) {
			return	(T)this._children.get(idx);
		}
		
        private void removeChild(final Node child) {
            this._children.remove(child);
        }

		public int getChildCount() {
			return	this._children.size();
		}
		
        /**
         * @return the _data
         */
        @SuppressWarnings("unchecked")
        public <T> T getData() {
            return (T)this._data;
        }

        /**
         * @param data the _data to set
         */
        public void setData(final Object data) {
            this._data = data;
        }

		@Override
		public String toString() {
			return "Node [_name=" + _name + ", _children=" + _children + "]";
		}


		private final String _name;
        private Object _data;
		private final List<Object> _children = new ArrayList<Object>();
	}
	
	public SimpleTreeModel(final Node root) {
		super(root);
	}

	@Override
	public boolean isLeaf(final Object node) {
		return getChildCount(node)==0;
	}

	@Override
	public Object getChild(final Object parent, int index) {
		return ( parent instanceof Node) 
				? ((Node)parent).getChild(index)
				: null;
	}

	@Override
	public int getChildCount(final Object parent) {
		return ( parent instanceof Node) 
				? ((Node)parent).getChildCount()
				: 0;
	}
}
