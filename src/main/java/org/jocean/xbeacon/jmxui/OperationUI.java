package org.jocean.xbeacon.jmxui;

import java.util.Arrays;

import org.jocean.jolokia.spi.ListResponse.ArgInfo;
import org.jocean.jolokia.spi.ListResponse.OperationInfo;
import org.jocean.zkoss.annotation.RowSource;
import org.zkoss.zk.ui.event.Events;
import org.zkoss.zul.Button;

import com.alibaba.fastjson.JSONArray;

import rx.functions.Action1;

public class OperationUI {
    public OperationUI(final OperationInfo op) {
        this._name = op.getName();
        this._args = from(op.getArgs());
        this._description = op.getDescription();
        this._argsAsText = genSignature();
        this._returnType = op.getReturnType();
    }

    private ArgUI[] from(final ArgInfo[] args) {
        if (null == args) {
            return null;
        }
        final ArgUI[] uis = new ArgUI[args.length];
        for (int idx = 0; idx < args.length; idx++) {
            uis[idx] = new ArgUI(args[idx]);
        }
        return uis;
    }

    public void setInvoker(final Action1<OperationUI> invoker) {
        this._btnInvoke = new Button("执行");
        this._btnInvoke.addEventListener(Events.ON_CLICK, event->invoker.call(this));
    }

    public String genNameWithSignature() {
        final StringBuilder sb = new StringBuilder();
        sb.append(this._name);
        sb.append(genSignature());
        return sb.toString();
    }

    private String genSignature() {
        final StringBuilder sb = new StringBuilder();
        String comma = "";
        sb.append('(');
        for (final ArgUI arg : this._args) {
            sb.append(comma);
            sb.append(arg.getType());
            comma = ",";
        }
        sb.append(')');
        return sb.toString();
    }

    public JSONArray genArgArray() {
        if (null == this._args || this._args.length == 0) {
            return null;
        } else {
            final JSONArray array = new JSONArray();
            for (final ArgUI arg : this._args) {
                array.add(arg.getInputObject());
            }
            return array;
        }
    }

    public String getName() {
        return this._name;
    }

    public ArgUI[] getArgs() {
        return this._args;
    }

    @RowSource(name = "执行")
    private Button _btnInvoke;

    @RowSource(name = "描述")
    private final String _description;

    @RowSource(name = "方法名")
    private final String _name;

    private final ArgUI[] _args;

    @RowSource(name = "参数")
    private final String _argsAsText;

    @RowSource(name = "返回类型")
    private final String _returnType;

    /*
     * (non-Javadoc)
     *
     * @see java.lang.Object#toString()
     */
    @Override
    public String toString() {
        final StringBuilder builder = new StringBuilder();
        builder.append("Op [args=").append(Arrays.toString(_args)).append(", ret=").append(_returnType)
                .append(", desc=").append(_description).append("]");
        return builder.toString();
    }
}
