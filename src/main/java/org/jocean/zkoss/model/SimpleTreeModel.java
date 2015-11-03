package org.jocean.zkoss.model;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.zkoss.zul.AbstractTreeModel;

public class SimpleTreeModel extends AbstractTreeModel<Object> {
	
	private static final long serialVersionUID = -6720368595112891822L;

	static public class Node {
		
		public Node(final String name) {
			this._name = name;
		}
		
		public Node getParent() {
		    return this._parent;
		}
		
		public Node addChildIfAbsent(final String nameForAdd) {
			for ( Node child : this._children ) {
				if ( child._name.equals(nameForAdd) ) {
					return	child;
				}
			}
			
			final Node toAdd = new Node(nameForAdd);
			toAdd._parent = this;
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
		
		public Node addChild(final Node child) {
            child._parent = this;
			this._children.add(child);
			return	this;
		}

		public Node addChildren(final Collection<Node> children) {
		    for (Node child : children) {
                child._parent = this;
            }
			this._children.addAll(children);
			return	this;
		}
		
        public Node removeChild(final String[] path) {
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
            for ( Node child : this._children ) {
                if ( child._name.equals(name) ) {
                    return  child;
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
        
        public Node getChild(final int idx) {
			return	this._children.get(idx);
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
        private Node _parent = null;
		private final List<Node> _children = new CopyOnWriteArrayList<>();
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
