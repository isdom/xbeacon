package org.jocean.xbeacon.jmxui.bean;

import com.alibaba.fastjson.annotation.JSONField;

public class JolokiaResponse {

    @JSONField(name = "request")
    public JolokiaRequest getRequest() {
        return _request;
    }

    @JSONField(name = "request")
    public void setRequest(final JolokiaRequest req) {
        this._request = req;
    }

    @JSONField(name = "timestamp")
    public long getTimestamp() {
        return _timestamp;
    }

    @JSONField(name = "timestamp")
    public void setTimestamp(final long timestamp) {
        this._timestamp = timestamp;
    }

    @JSONField(name = "status")
    public int getStatus() {
        return _status;
    }

    @JSONField(name = "status")
    public void setStatus(final int status) {
        this._status = status;
    }

    protected JolokiaRequest _request;
    protected long _timestamp;
    protected int _status;

    public JolokiaResponse() {
        super();
    }

    /* (non-Javadoc)
     * @see java.lang.Object#toString()
     */
    @Override
    public String toString() {
        final StringBuilder builder = new StringBuilder();
        builder.append("JolokiaResponse [request=").append(_request)
                .append(", timestamp=").append(_timestamp).append(", status=")
                .append(_status).append("]");
        return builder.toString();
    }
}