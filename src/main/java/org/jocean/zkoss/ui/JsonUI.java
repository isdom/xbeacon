package org.jocean.zkoss.ui;

import java.util.Arrays;
import java.util.Map;

import org.jocean.zkoss.annotation.RowSource;
import org.jocean.zkoss.builder.UIBuilders;
import org.jocean.zkoss.builder.ZModels;
import org.zkoss.zk.ui.Component;
import org.zkoss.zk.ui.IdSpace;
import org.zkoss.zk.ui.ext.Scope;
import org.zkoss.zk.ui.ext.ScopeListener;
import org.zkoss.zul.Listbox;
import org.zkoss.zul.Listhead;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;

public class JsonUI {
    public static Object buildUI(final Object value) {
        if (value instanceof JSONObject) {
            return buildGridOfJSON((JSONObject)value);
        } else if (value instanceof JSONArray) {
            return buildGridOfArray((JSONArray)value);
        }
        
        return value;
    }
    
    private static class ObjectRow {
        void setValue(final Object value) {
            this._value = buildUI(value);
            if (null != value) {
                this._valueType = value.getClass().getSimpleName();
            }
        }

        @RowSource(name="内容")
        Object _value;
        
        @RowSource(name="类型")
        String _valueType;
    }
    
    private static class AttrValue implements Comparable<AttrValue> {
        
        @Override
        public int compareTo(final AttrValue o) {
            return this._name.compareTo(o._name);
        }
        
        void setName(final String name) {
            this._name = name;
        }
        
        void setValue(final Object value) {
            this._value = buildUI(value);
            if (null != value) {
                this._valueType = value.getClass().getSimpleName();
            }
        }
        
        @RowSource(name="属性名")
        String _name;
        
        @RowSource(name="值")
        Object _value;
        
        @RowSource(name="类型")
        String _valueType;
    }
    
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
}
