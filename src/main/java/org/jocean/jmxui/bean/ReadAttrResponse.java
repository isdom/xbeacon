package org.jocean.jmxui.bean;

import com.alibaba.fastjson.annotation.JSONField;

public class ReadAttrResponse extends JolokiaResponse {
    @JSONField(serialize=false)
    public Object getValue() {
        return _value;
    }

    @JSONField(name="value")
    public void setValue(final Object value) {
        this._value = value;
    }

    private Object _value;
}
