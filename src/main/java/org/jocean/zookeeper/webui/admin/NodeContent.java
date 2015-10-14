package org.jocean.zookeeper.webui.admin;

import org.jocean.zkoss.model.SimpleTreeModel.Node;
import org.zkoss.zk.ui.event.Event;
import org.zkoss.zk.ui.event.EventListener;
import org.zkoss.zk.ui.event.Events;
import org.zkoss.zk.ui.event.InputEvent;
import org.zkoss.zul.Menubar;
import org.zkoss.zul.Menuitem;
import org.zkoss.zul.Messagebox;
import org.zkoss.zul.Tab;
import org.zkoss.zul.Tabpanel;
import org.zkoss.zul.Tabpanels;
import org.zkoss.zul.Tabs;
import org.zkoss.zul.Textbox;

import rx.functions.Action0;

class NodeContent {
    private final ZKAgent _zka;
    private final Node _node;
    private final Tab _tab;
    private final Textbox _textbox;
    private final Menuitem _save;
    private boolean _isModified = false;
    
    NodeContent(final ZKAgent zka, 
            final Node node, 
            final String path, 
            final Tabs tabs, 
            final Tabpanels tabpanels,
            final Action0 onClose) {
        this._zka = zka;
        this._node = node;
        this._tab = new Tab(path) {
            void doClose() {
                super.close();
                onClose.call();
            }
            
            /* (non-Javadoc)
             * @see org.zkoss.zul.Tab#close()
             */
            @Override
            public void close() {
                if (isModified()) {
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
            private static final long serialVersionUID = 1L;
            {
                this.setClosable(true);
            }};
        tabs.appendChild(this._tab);
        this._textbox = new Textbox() {
            private static final long serialVersionUID = 1L;
        {
            this.setWidth("100%");
            this.setHeight("100%");
            this.setMultiline(true);
            this.setText(NodeContent.this._zka.getNodeDataAsString(node));
        }};
        this._textbox.addEventListener(Events.ON_CHANGING, new EventListener<InputEvent>() {
            @Override
            public void onEvent(final InputEvent event) throws Exception {
                markModified();
            }});
        this._save = new Menuitem("save") {
            private static final long serialVersionUID = 1L;
        {
            this.setDisabled(true);
        }};
        this._save.addEventListener(Events.ON_CLICK, new EventListener<Event>() {
            @Override
            public void onEvent(final Event event) throws Exception {
                saveContentToZK();
            }});
        tabpanels.appendChild(new Tabpanel() {
            private static final long serialVersionUID = 1L; {
                this.appendChild(new Menubar() {
                    private static final long serialVersionUID = 1L;
                    {
                        this.appendChild(_save);
                    }
                });
                this.appendChild(_textbox);
            }});
        this._tab.setSelected(true);
    }
    
    void select() {
        this._tab.setSelected(true);
    }

    private boolean isModified() {
        return this._isModified;
    }
    
    private void markModified() {
        if (!this._isModified) {
            this._isModified = true;
            this._tab.setLabel(this._zka.getNodePath(this._node) + " *");
            this._save.setDisabled(false);
        }
    }
    
    private void saveContentToZK() throws Exception {
        this._zka.setNodeDataAsString( this._node,  this._textbox.getText());
        this._isModified = false;
        this._tab.setLabel(this._zka.getNodePath(this._node));
        this._save.setDisabled(true);
    }
}