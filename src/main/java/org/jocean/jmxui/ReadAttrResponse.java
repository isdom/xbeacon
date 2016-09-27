package org.jocean.jmxui;

import java.util.Arrays;
import java.util.Map;

import org.jocean.zkoss.annotation.RowSource;
import org.jocean.zkoss.builder.GridBuilder;
import org.zkoss.zul.Columns;
import org.zkoss.zul.Grid;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.annotation.JSONField;

public class ReadAttrResponse {
    public static class AttrValue implements Comparable<AttrValue> {
        
        @Override
        public int compareTo(final AttrValue o) {
            return this._name.compareTo(o._name);
        }
        
        public void setName(final String name) {
            this._name = name;
        }
        
        public void setValue(final Object value) {
            class ObjectRow {
                @RowSource(name="内容")
                Object value;
            }
            if (value instanceof JSONArray) {
                final JSONArray jarray = (JSONArray)value;
                final ObjectRow[] array = new ObjectRow[jarray.size()];
                for (int idx = 0; idx < array.length; idx++) {
                    array[idx].value = jarray.get(idx);
                }
                final Grid grid = new Grid();
                grid.appendChild(new Columns() {
                    private static final long serialVersionUID = 1L;
                {
                    this.setSizable(true);
                    GridBuilder.buildColumns(this, ObjectRow.class);
                }});
                grid.setModel( GridBuilder.buildListModel(ObjectRow.class, 
                        array.length, 
                        GridBuilder.fetchPageOf(array),
                        GridBuilder.fetchTotalSizeOf(array)));
                this._value = grid;
            } else {
                this._value = value;
            }
            if (null != value) {
                this._valueType = value.getClass();
            }
        }
        
        @RowSource(name="属性名")
        private String _name;
        
        @RowSource(name="值")
        private Object _value;
        
        @RowSource(name="类型")
        private Class<?> _valueType;
    }
    
    @JSONField(name="request")
    public JolokiaRequest getRequest() {
        return _request;
    }

    @JSONField(name="request")
    public void setRequest(final JolokiaRequest req) {
        this._request = req;
    }

    @JSONField(name="status")
    public int getStatus() {
        return _status;
    }

    @JSONField(name="status")
    public void setStatus(final int status) {
        this._status = status;
    }
    
    @JSONField(serialize=false)
    public AttrValue[] getValue() {
        return _value;
    }

    @JSONField(name="value")
    public void setValue(final Map<String, Object> value) {
        this._value = new AttrValue[value.size()];
        int idx = 0;
        for (Map.Entry<String, Object> entry : value.entrySet()) {
            final AttrValue attrv = new AttrValue();
            attrv.setName(entry.getKey());
            attrv.setValue(entry.getValue());
            this._value[idx++] = attrv;
        }
        Arrays.sort(this._value);
    }

    private JolokiaRequest _request;
    private AttrValue[] _value;
    private int   _status;
}
