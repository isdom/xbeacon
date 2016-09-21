package org.jocean.jmxui;

import com.alibaba.fastjson.annotation.JSONField;

public class JolokiaResponse {
    @JSONField(name="request")
    public JolokiaRequest getRequest() {
        return _request;
    }

    @JSONField(name="request")
    public void setRequest(final JolokiaRequest req) {
        this._request = req;
    }

    @JSONField(name="value")
    public String[] getValue() {
        return _value;
    }

    @JSONField(name="value")
    public void setValue(final String[] value) {
        this._value = value;
    }

    private JolokiaRequest _request;
    private String[] _value;
}
