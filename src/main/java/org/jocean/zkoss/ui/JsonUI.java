package org.jocean.zkoss.ui;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.jocean.zkoss.annotation.RowSource;
import org.jocean.zkoss.builder.UIBuilders;
import org.jocean.zkoss.builder.ZModels;
import org.zkoss.zk.ui.Component;
import org.zkoss.zk.ui.IdSpace;
import org.zkoss.zk.ui.event.EventListener;
import org.zkoss.zk.ui.event.Events;
import org.zkoss.zk.ui.event.SelectEvent;
import org.zkoss.zk.ui.ext.Scope;
import org.zkoss.zk.ui.ext.ScopeListener;
import org.zkoss.zul.ListModel;
import org.zkoss.zul.Listbox;
import org.zkoss.zul.Listhead;
import org.zkoss.zul.Listitem;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.google.common.collect.Lists;

public class JsonUI {
    public static Component buildUI(final Object value) {
        if (value instanceof JSONObject) {
//            return buildGridOfJSON((JSONObject)value);
            return buildUI4JSON((JSONObject)value);
        } else if (value instanceof JSONArray) {
//            return buildGridOfArray((JSONArray)value);
            return buildUI4Array((JSONArray)value);
        }
        
        return null;
    }
    
    private static class ObjectRow {
        void setValue(final Object value) {
            if (value instanceof JSONObject 
                || value instanceof JSONArray) {
                this._mapOrArray = value;
                this._value = value.getClass().getSimpleName();
            } else {
                this._value = value;
            }
            if (null != value) {
                this._valueType = value.getClass().getSimpleName();
            }
        }
        
        @RowSource(name="类型")
        String _valueType;
        
        @RowSource(name="内容")
        Object _value;
        
        Object _mapOrArray = null;
        Component _mapOrArrayUI = null;
    }
    
    private static class AttrValue extends ObjectRow implements Comparable<AttrValue> {
        
        @Override
        public int compareTo(final AttrValue o) {
            return this._name.compareTo(o._name);
        }
        
        void setName(final String name) {
            this._name = name;
        }
        
        @RowSource(name="属性名")
        String _name;
    }
    
    @SuppressWarnings("unused")
    private static Component buildGridOfJSON(final JSONObject jobj) {
        final AttrValue[] attrs = new AttrValue[jobj.size()];
        int idx = 0;
        for (Map.Entry<String, Object> entry : jobj.entrySet()) {
            final AttrValue attrv = new AttrValue();
            attrv.setName(entry.getKey());
            attrv.setValue(entry.getValue());
            attrs[idx++] = attrv;
        }
        Arrays.sort(attrs);
//        final Grid grid = new Grid();
        final Listbox grid = new Listbox();
//        grid.setStyle(style);
//        grid.setRowRenderer(
//                GridBuilder.buildRowRenderer(AttrValue.class));
        grid.setItemRenderer(UIBuilders.buildItemRenderer(AttrValue.class));
        grid.setSizedByContent(true);
        grid.appendChild(new Listhead() {
            private static final long serialVersionUID = 1L;
            {
                this.setSizable(true);
                UIBuilders.buildHead(this, AttrValue.class);
            }
        });
//        grid.appendChild(new Columns() {
//            private static final long serialVersionUID = 1L;
//        {
//            this.setSizable(true);
//            GridBuilder.buildColumns(this, AttrValue.class);
//        }});
        grid.addScopeListener(new ScopeListener() {
            @Override
            public void attributeAdded(Scope scope, String name,
                    Object value) {
            }

            @Override
            public void attributeReplaced(Scope scope, String name,
                    Object value) {
            }

            @Override
            public void attributeRemoved(Scope scope, String name) {
            }

            @Override
            public void parentChanged(final Scope scope, final Scope newparent) {
                grid.setModel( ZModels.buildListModel(
                        attrs.length, 
                        ZModels.fetchPageOf(attrs),
                        ZModels.fetchTotalSizeOf(attrs)));
            }

            @Override
            public void idSpaceChanged(Scope scope,
                    IdSpace newIdSpace) {
            }});
        return grid;
    }

    @SuppressWarnings("unused")
    private static Component buildGridOfArray(final JSONArray jarray) {
        final ObjectRow[] array = new ObjectRow[jarray.size()];
        for (int idx = 0; idx < array.length; idx++) {
            final ObjectRow row = new ObjectRow();
            row.setValue(jarray.get(idx));
            array[idx] = row;
        }
//        final Grid grid = new Grid();
        final Listbox grid = new Listbox();
//        grid.setRowRenderer(GridBuilder.buildRowRenderer(ObjectRow.class));
        grid.setItemRenderer(UIBuilders.buildItemRenderer(ObjectRow.class));
        grid.setSizedByContent(true);
        grid.appendChild(new Listhead() {
            private static final long serialVersionUID = 1L;
            {
                this.setSizable(true);
                UIBuilders.buildHead(this, ObjectRow.class);
            }
        });
//        grid.appendChild(new Columns() {
//            private static final long serialVersionUID = 1L;
//        {
//            this.setSizable(true);
//            GridBuilder.buildColumns(this, ObjectRow.class);
//        }});
        grid.addScopeListener(new ScopeListener() {
            @Override
            public void attributeAdded(Scope scope, String name,
                    Object value) {
            }

            @Override
            public void attributeReplaced(Scope scope, String name,
                    Object value) {
            }

            @Override
            public void attributeRemoved(Scope scope, String name) {
            }

            @Override
            public void parentChanged(final Scope scope, final Scope newparent) {
                grid.setModel( ZModels.buildListModel(
                        array.length, 
                        ZModels.fetchPageOf(array),
                        ZModels.fetchTotalSizeOf(array)));
            }

            @Override
            public void idSpaceChanged(Scope scope,
                    IdSpace newIdSpace) {
            }});
        return grid;
    }
    
    private static Component buildUI4JSON(final JSONObject jobj) {
        final AttrValue[] attrs = new AttrValue[jobj.size()];
        int idx = 0;
        for (Map.Entry<String, Object> entry : jobj.entrySet()) {
            final AttrValue attrv = new AttrValue();
            attrv.setName(entry.getKey());
            attrv.setValue(entry.getValue());
            attrs[idx++] = attrv;
        }
        Arrays.sort(attrs);
        return buildListbox(AttrValue.class, attrs);
    }

    private static Component buildUI4Array(final JSONArray jarray) {
        final ObjectRow[] array = new ObjectRow[jarray.size()];
        for (int idx = 0; idx < array.length; idx++) {
            final ObjectRow row = new ObjectRow();
            row.setValue(jarray.get(idx));
            array[idx] = row;
        }
        return buildListbox(ObjectRow.class, array);
    }

    private static <T extends ObjectRow> Component buildListbox(final Class<T> cls, final T[] array) {
        final Listbox listbox = new Listbox();
        listbox.setItemRenderer(UIBuilders.buildItemRenderer(cls));
        listbox.setSizedByContent(true);
        
        final List<Component> descendants = Lists.newArrayList();
        listbox.appendChild(new Listhead() {
            private static final long serialVersionUID = 1L;
            {
                this.setSizable(true);
                UIBuilders.buildHead(this, cls);
            }
        });
        listbox.addScopeListener(new ScopeListener() {
            @Override
            public void attributeAdded(Scope scope, String name,
                    Object value) {
            }

            @Override
            public void attributeReplaced(Scope scope, String name,
                    Object value) {
            }

            @Override
            public void attributeRemoved(Scope scope, String name) {
            }

            @Override
            public void parentChanged(final Scope scope, final Scope newparent) {
                setModelAndEnableSelRow((Component)newparent, listbox, array, descendants);
            }

            @Override
            public void idSpaceChanged(Scope scope,
                    IdSpace newIdSpace) {
            }});
        return listbox;
    }

    private static <T extends ObjectRow> void setModelAndEnableSelRow(
            final Component newparent, 
            final Listbox listbox,
            final T[] rows, 
            final List<Component> descendants) {
        if (null != newparent) {
            //  attach to page
            final ListModel<T> model = ZModels.buildListModel(
                    rows.length, 
                    ZModels.fetchPageOf(rows),
                    ZModels.fetchTotalSizeOf(rows));
            listbox.addEventListener(Events.ON_SELECT, 
                    new EventListener<SelectEvent<Listitem, ObjectRow>>() {
                @Override
                public void onEvent(final SelectEvent<Listitem, ObjectRow> event)
                        throws Exception {
                    if (!event.getUnselectedObjects().isEmpty()) {
                        final ObjectRow tohide = event.getUnselectedObjects().iterator().next();
                        if (null != tohide._mapOrArrayUI) {
                            descendants.remove(tohide._mapOrArrayUI);
                            newparent.removeChild(tohide._mapOrArrayUI);
                            tohide._mapOrArrayUI = null;
                        }
                    }
                    final ObjectRow toshow = event.getSelectedObjects().iterator().next();
                    if ( null != toshow._mapOrArray) {
                        toshow._mapOrArrayUI = buildUI(toshow._mapOrArray);
                        newparent.appendChild(toshow._mapOrArrayUI);
                        descendants.add(toshow._mapOrArrayUI);
                        //  remove from parent
                        //  remove all: ancestor / descendant
                    }
                }});
            listbox.setModel(model);
        } else if (null == newparent) {
            //  detach from page
            // remove  descendants
            for (Component descendant : descendants) {
                descendant.detach();
            }
            descendants.clear();
        }
    }
}
