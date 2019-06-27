package org.jocean.xbeacon.api;

import org.jocean.j2se.annotation.Updatable;
import org.springframework.beans.factory.annotation.Value;

import com.alibaba.fastjson.annotation.JSONField;

@Updatable
public class OperationInfo implements Comparable<OperationInfo> {

    @JSONField(name = "hostname")
    public String getHostname() {
        return _hostname;
    }

    @JSONField(name = "ip")
    public String getIp() {
        return _ip;
    }

    @JSONField(name = "service")
    public String getService() {
        return _service;
    }

    @JSONField(name = "pid")
    public String getPid() {
        return _pid;
    }

    @JSONField(name = "path")
    public String getPath() {
        return _path;
    }

    @JSONField(name = "port")
    public int getPort() {
        return _port;
    }

    @JSONField(name = "operation")
    public String getOperation() {
        return _operation;
    }

    @JSONField(name = "tradeCount")
    public int getTradeCount() {
        return _tradeCount;
    }

    @Value("${hostname}")
    String _hostname;

    @Value("${ip}")
    String _ip;

    @Value("${service}")
    String _service;

    @Value("${pid}")
    String _pid;

    @Value("${request.path}")
    String _path;

    @Value("${port}")
    int _port;

    @Value("${operation}")
    String _operation;

    @Value("${trade.count}")
    int _tradeCount;

    @Override
    public int compareTo(final OperationInfo o) {
        int ret = this._hostname.compareTo(o._hostname);
        if (ret != 0) {
            return ret;
        }
        ret = this._ip.compareTo(o._ip);
        if (ret != 0) {
            return ret;
        }
        ret = this._service.compareTo(o._service);
        if (ret != 0) {
            return ret;
        }
        ret = this._pid.compareTo(o._pid);
        if (ret != 0) {
            return ret;
        }
        ret = this._path.compareTo(o._path);
        if (ret != 0) {
            return ret;
        }

        ret = this._port - o._port;
        if (ret != 0) {
            return ret;
        }
        ret = this._operation.compareTo(o._operation);
        if (ret != 0) {
            return ret;
        }
        return this._tradeCount - o._tradeCount;
    }
}
