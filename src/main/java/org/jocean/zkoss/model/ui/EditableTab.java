package org.jocean.zkoss.model.ui;

import org.zkoss.zk.ui.Component;
import org.zkoss.zk.ui.event.CheckEvent;
import org.zkoss.zk.ui.event.Event;
import org.zkoss.zk.ui.event.EventListener;
import org.zkoss.zk.ui.event.Events;
import org.zkoss.zul.Checkbox;
import org.zkoss.zul.Messagebox;
import org.zkoss.zul.Separator;
import org.zkoss.zul.Tab;
import org.zkoss.zul.Tabpanel;
import org.zkoss.zul.Tabpanels;
import org.zkoss.zul.Tabs;
import org.zkoss.zul.Toolbar;
import org.zkoss.zul.Toolbarbutton;

import rx.functions.Action0;
import rx.functions.Action1;

public class EditableTab {
    public EditableTab(final String title) {
        this._title = title;
        this._tab = new Tab(title) {
            void doClose() {
                super.close();
                if (null!= _onClose) {
                    _onClose.call();
                }
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
        this._apply = new Toolbarbutton("Apply");
        this._apply.setDisabled(true);
        this._apply.addEventListener(Events.ON_CLICK, new EventListener<Event>() {
            @Override
            public void onEvent(final Event event) throws Exception {
                doApply();
            }});
        final Checkbox cb = new Checkbox("Enable Edit");
        cb.addEventListener(Events.ON_CHECK, new EventListener<CheckEvent>() {
            @Override
            public void onEvent(final CheckEvent event) throws Exception {
                if (null!=_onEnableEdit) {
                    _onEnableEdit.call(event.isChecked());
                }
            }});
        this._tabpanel = new Tabpanel() {
            private static final long serialVersionUID = 1L; {
                this.appendChild(new Toolbar() {
                    private static final long serialVersionUID = 1L;
                    {
                        this.appendChild(cb);
                        this.appendChild(new Separator() {
                            private static final long serialVersionUID = 1L;
                        {
                            this.setOrient("vertical");
                            this.setBar(true);
                            this.setSpacing("40px");
                        }});
                        this.appendChild(_apply);
                    }
                });
            }};
    }
    
    public void close() {
        this._isModified = false;
        this._tab.close();
    }
    
    public EditableTab setOnClose(final Action0 onClose) {
        this._onClose = onClose;
        return this;
    }
    
    public EditableTab setOnApply(final Action0 onApply) {
        this._onApply = onApply;
        return this;
    }
    
    public EditableTab setOnEnableEdit(final Action1<Boolean> onEnableEdit) {
        this._onEnableEdit = onEnableEdit;
        return this;
    }
    
    public EditableTab appendChild(final Component component) {
        this._tabpanel.appendChild(component);
        return this;
    }
    
    public EditableTab appendToTabs(final Tabs tabs) {
        tabs.appendChild(this._tab);
        this._tab.setSelected(true);
        return this;
    }
    
    public EditableTab appendToTabpanels(final Tabpanels tabpanels) {
        tabpanels.appendChild(this._tabpanel);
        return this;
    }
    
    public void setSelected() {
        this._tab.setSelected(true);
    }

    private boolean isModified() {
        return this._isModified;
    }
    
    public void markModified() {
        if (!this._isModified) {
            this._isModified = true;
            this._tab.setLabel(this._title + " *");
            this._apply.setDisabled(false);
        }
    }
    
    private void doApply() {
        if (null!=this._onApply) {
            this._onApply.call();
        }
        this._isModified = false;
        this._tab.setLabel(this._title);
        this._apply.setDisabled(true);
    }

    private final String _title;
    private final Tab _tab;
    private final Tabpanel _tabpanel;
    private final Toolbarbutton _apply;
    private boolean _isModified = false;
    private Action0 _onClose = null;
    private Action0 _onApply = null;
    private Action1<Boolean> _onEnableEdit = null;
}
