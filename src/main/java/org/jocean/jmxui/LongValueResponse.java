package org.jocean.jmxui;

import com.alibaba.fastjson.annotation.JSONField;

public class LongValueResponse {
    
    @JSONField(name="request")
    public JolokiaRequest getRequest() {
        return _request;
    }

    @JSONField(name="request")
    public void setRequest(final JolokiaRequest req) {
        this._request = req;
    }

    @JSONField(name="value")
    public long getValue() {
        return _value;
    }

    @JSONField(name="value")
    public void setValue(final long value) {
        this._value = value;
    }
    
    private JolokiaRequest _request;
    private long _value;
    /* (non-Javadoc)
     * @see java.lang.Object#toString()
     */
    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("LongValueResponse [request=").append(_request)
                .append(", value=").append(_value).append("]");
        return builder.toString();
    }
}
