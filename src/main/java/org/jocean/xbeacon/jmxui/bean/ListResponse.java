package org.jocean.xbeacon.jmxui.bean;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import javax.management.ObjectName;

import org.jocean.jolokia.spi.JolokiaResponse;
import org.jocean.zkoss.annotation.RowSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zkoss.zk.ui.event.EventListener;
import org.zkoss.zk.ui.event.Events;
import org.zkoss.zk.ui.event.MouseEvent;
import org.zkoss.zul.Button;
import org.zkoss.zul.Doublebox;
import org.zkoss.zul.Intbox;
import org.zkoss.zul.Longbox;
import org.zkoss.zul.Textbox;
import org.zkoss.zul.impl.InputElement;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.annotation.JSONField;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

import rx.functions.Action1;

public class ListResponse extends JolokiaResponse {
    @SuppressWarnings("unused")
    private static final Logger LOG =
            LoggerFactory.getLogger(ListResponse.class);

    public static class ArgInfo {
        static interface Input {
            public Object getInputObject(final InputElement input);
            public InputElement newInputComponent();
        }

        private static Map<String, Input> _TYPE2INPUT;
        private static Input INPUT_TEXT;
        private static Input type2input(final String type) {
            final Input input = _TYPE2INPUT.get(type);
            return null != input ? input : INPUT_TEXT;
        }

        static {
            final Input INPUT_INT = new Input() {
                @Override
                public Object getInputObject(final InputElement input) {
                    return ((Intbox)input).getValue();
                }
                @Override
                public InputElement newInputComponent() {
                    return new Intbox();
                }};
            final Input INPUT_LONG = new Input() {
                @Override
                public Object getInputObject(final InputElement input) {
                    return ((Longbox)input).getValue();
                }
                @Override
                public InputElement newInputComponent() {
                    return new Longbox();
                }};

            final Input INPUT_DOUBLE = new Input() {
                @Override
                public Object getInputObject(final InputElement input) {
                    return ((Doublebox)input).getValue();
                }
                @Override
                public InputElement newInputComponent() {
                    return new Doublebox();
                }};
            INPUT_TEXT = new Input() {
                @Override
                public Object getInputObject(final InputElement input) {
                    return ((Textbox)input).getValue();
                }
                @Override
                public InputElement newInputComponent() {
                    return new Textbox();
                }};
            _TYPE2INPUT = Maps.newHashMap();
            _TYPE2INPUT.put("byte", INPUT_INT);
            _TYPE2INPUT.put("java.lang.Byte", INPUT_INT);
            _TYPE2INPUT.put("short", INPUT_INT);
            _TYPE2INPUT.put("java.lang.Short", INPUT_INT);
            _TYPE2INPUT.put("int", INPUT_INT);
            _TYPE2INPUT.put("java.lang.Integer", INPUT_INT);
            _TYPE2INPUT.put("long", INPUT_LONG);
            _TYPE2INPUT.put("java.lang.Long", INPUT_LONG);
            _TYPE2INPUT.put("float", INPUT_DOUBLE);
            _TYPE2INPUT.put("java.lang.Float", INPUT_DOUBLE);
            _TYPE2INPUT.put("double", INPUT_DOUBLE);
            _TYPE2INPUT.put("java.lang.Double", INPUT_DOUBLE);
        }

        @JSONField(name="name")
        public String getName() {
            return this._name;
        }

        @JSONField(name="name")
        public void setName(final String name) {
            this._name = name;
        }

        @JSONField(name="type")
        public String getType() {
            return this._type;
        }

        @JSONField(name="type")
        public void setType(final String type) {
            this._type = type;
            buildInput();
        }

        @JSONField(name="desc")
        public String getDescription() {
            return this._description;
        }

        @JSONField(name="desc")
        public void setDescription(final String desc) {
            this._description = desc;
        }

        public void buildInput() {
            this._input = type2input(this._type);
            this._inputComponent = this._input.newInputComponent();
        }

        public Object getInputObject() {
            return this._input.getInputObject(_inputComponent);
        }

        @RowSource(name="描述")
        private String _description;

        @RowSource(name="参数")
        private String _name;

        @RowSource(name="类型")
        private String _type;

        @RowSource(name="输入值")
        private InputElement _inputComponent;

        private Input _input;

        /* (non-Javadoc)
         * @see java.lang.Object#toString()
         */
        @Override
        public String toString() {
            final StringBuilder builder = new StringBuilder();
            builder.append("[name=").append(_name).append(", type=")
                    .append(_type).append(", desc=")
                    .append(_description).append("]");
            return builder.toString();
        }
    }

    public static class OperationInfo implements Comparable<OperationInfo> {

        public void setInvoker(final Action1<OperationInfo> invoker) {
            this._btnInvoke = new Button("执行");
            this._btnInvoke.addEventListener(Events.ON_CLICK,
                new EventListener<MouseEvent>() {
                    @Override
                    public void onEvent(final MouseEvent event) throws Exception {
                        invoker.call(OperationInfo.this);
                    }});
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
            for (final ArgInfo arg : this._args) {
                sb.append(comma);
                sb.append(arg.getType());
                comma = ",";
            }
            sb.append(')');
            return sb.toString();
        }

        public JSONArray genArgArray() {
            if (null == this._args ||
                    this._args.length == 0) {
                return null;
            } else {
                final JSONArray array = new JSONArray();
                for (final ArgInfo arg : this._args) {
                    array.add(arg.getInputObject());
                }
                return array;
            }
        }

        @Override
        public int compareTo(final OperationInfo o) {
            return this._name.compareTo(o._name);
        }

        @JSONField(serialize=false)
        public String getName() {
            return this._name;
        }

        @JSONField(deserialize=false)
        public void setName(final String name) {
            this._name = name;
        }

        @JSONField(name="args")
        public ArgInfo[] getArgs() {
            return this._args;
        }

        @JSONField(name="args")
        public void setArgs(final ArgInfo[] args) {
            this._args = args;
            this._argsAsText = genSignature();
        }

        @JSONField(name="ret")
        public String getReturnType() {
            return this._returnType;
        }

        @JSONField(name="ret")
        public void setReturnType(final String type) {
            this._returnType = type;
        }

        @JSONField(name="desc")
        public String getDescription() {
            return this._description;
        }

        @JSONField(name="desc")
        public void setDescription(final String desc) {
            this._description = desc;
        }

        @RowSource(name = "执行")
        private Button _btnInvoke;

        @RowSource(name="描述")
        private String _description;

        @RowSource(name="方法名")
        private String _name;

        private ArgInfo[] _args;

        @RowSource(name="参数")
        private String _argsAsText;

        @RowSource(name="返回类型")
        private String _returnType;

        /* (non-Javadoc)
         * @see java.lang.Object#toString()
         */
        @Override
        public String toString() {
            final StringBuilder builder = new StringBuilder();
            builder.append("Op [args=")
                    .append(Arrays.toString(_args)).append(", ret=")
                    .append(_returnType).append(", desc=")
                    .append(_description).append("]");
            return builder.toString();
        }
    }

    public static class AttributeInfo implements Comparable<AttributeInfo> {

        @Override
        public int compareTo(final AttributeInfo o) {
            return this._name.compareTo(o._name);
        }

        @JSONField(serialize=false)
        public String getName() {
            return this._name;
        }

        @JSONField(deserialize=false)
        public void setName(final String name) {
            this._name = name;
        }

        @JSONField(name="rw")
        public boolean isWriteable() {
            return this._writeable;
        }

        @JSONField(name="rw")
        public void setWriteable(final boolean writeable) {
            this._writeable = writeable;
        }

        @JSONField(name="type")
        public String getType() {
            return this._type;
        }

        @JSONField(name="type")
        public void setType(final String type) {
            this._type = type;
        }

        @JSONField(name="desc")
        public String getDescription() {
            return this._description;
        }

        @JSONField(name="desc")
        public void setDescription(final String desc) {
            this._description = desc;
        }

        @RowSource(name="属性名")
        private String _name;

        @RowSource(name="可写")
        private boolean _writeable;

        @RowSource(name="数据类型")
        private String _type;

        @RowSource(name="描述")
        private String _description;

        /* (non-Javadoc)
         * @see java.lang.Object#toString()
         */
        @Override
        public String toString() {
            final StringBuilder builder = new StringBuilder();
            builder.append("Attr [name=").append(_name)
                    .append(", rw=").append(_writeable)
                    .append(", type=").append(_type).append(", desc=")
                    .append(_description).append("]");
            return builder.toString();
        }
    }

    public static class MBeanInfo implements Comparable<MBeanInfo> {

        private static final OperationInfo[] EMPTY_OP = new OperationInfo[0];

        @Override
        public int compareTo(final MBeanInfo o) {
            return this._objname.compareTo(o._objname);
        }

        public void setObjectName(final String objname) {
            try {
                this._objname = ObjectName.getInstance(objname);
            } catch (final Exception e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }

        public ObjectName getObjectName() {
            return this._objname;
        }

        @JSONField(name="op")
        public OperationInfo[] getOperations() {
            return _operations;
        }

        @JSONField(name="op")
        public void setOperations(final Map<String, Object> operations) {
            if (null != operations) {
                final List<OperationInfo> ops = Lists.newArrayList();
                for (final Map.Entry<String, Object> entry : operations.entrySet()) {
                    if (entry.getValue() instanceof JSONArray) {
                        final OperationInfo[] infos =
                                JSON.toJavaObject((JSONArray)entry.getValue(), OperationInfo[].class);
                        for (final OperationInfo info : infos) {
                            info.setName(entry.getKey());
                        }
                        ops.addAll(Arrays.asList(infos));
                    } else {
                        final OperationInfo info =
                                JSON.toJavaObject((JSONObject)entry.getValue(), OperationInfo.class);
                        info.setName(entry.getKey());
                        ops.add(info);
                    }
                }
                this._operations = ops.toArray(EMPTY_OP);
                Arrays.sort(this._operations);
            }
        }

        public AttributeInfo[] getAttributes() {
            return _attributes;
        }

        @JSONField(name="attr")
        public void setAttributes(final Map<String, AttributeInfo> attributes) {
            this._attributes = new AttributeInfo[attributes.size()];
            int idx = 0;
            for (final Map.Entry<String, AttributeInfo> entry : attributes.entrySet()) {
                entry.getValue().setName(entry.getKey());
                this._attributes[idx++] = entry.getValue();
            }
            Arrays.sort(this._attributes);
        }

        @JSONField(name="desc")
        public String getDescription() {
            return this._description;
        }

        @JSONField(name="desc")
        public void setDescription(final String desc) {
            this._description = desc;
        }

        private ObjectName _objname;
        private OperationInfo[] _operations;
        private AttributeInfo[] _attributes;
        private String _description;
        /* (non-Javadoc)
         * @see java.lang.Object#toString()
         */
        @Override
        public String toString() {
            final StringBuilder builder = new StringBuilder();
            builder.append("\nMBean [op=");
            if (null != _operations) {
                for (final OperationInfo info : this._operations) {
                    builder.append("\n\t\t").append(info.toString());
                }
            } else {
                builder.append("null");
            }
            builder.append(", \n\tattr=");
            if (null != _attributes) {
                for (final AttributeInfo attr : this._attributes) {
                    builder.append("\n\t\t").append(attr.toString());
                }
            } else {
                builder.append("null");
            }
            builder.append(", \n\tdesc=").append(_description).append("]");
            return builder.toString();
        }
    }

    public static class DomainInfo implements Comparable<DomainInfo> {
        @Override
        public int compareTo(final DomainInfo o) {
            return this._name.compareTo(o._name);
        }

        public DomainInfo(final String name, final MBeanInfo[] mbeans) {
            this._name = name;
            this._mbeans = mbeans;
        }

        public String getName() {
            return this._name;
        }

        public MBeanInfo[] getMBeans() {
            return this._mbeans;
        }

        /* (non-Javadoc)
         * @see java.lang.Object#toString()
         */
        @Override
        public String toString() {
            final StringBuilder builder = new StringBuilder();
            builder.append("DomainInfo [domain=").append(_name)
                    .append(", mbeans=").append(Arrays.toString(_mbeans))
                    .append("]");
            return builder.toString();
        }

        private final String _name;
        private final MBeanInfo[] _mbeans;
    }

    @JSONField(serialize=false)
    public DomainInfo[] getDomains() {
        return this._domains;
    }

    @JSONField(name="value")
    public void setDomains(final Map<String, Map<String, MBeanInfo>> value) {
        this._domains = new DomainInfo[value.size()];
        int domainidx = 0;
        for (final Map.Entry<String, Map<String, MBeanInfo>> entry : value.entrySet()) {
            final MBeanInfo[] mbeans = new MBeanInfo[entry.getValue().size()];
            int idx = 0;
            for (final Map.Entry<String, MBeanInfo> mbeanentry : entry.getValue().entrySet()) {
                mbeanentry.getValue().setObjectName(entry.getKey() + ":" + mbeanentry.getKey());
                mbeans[idx++] = mbeanentry.getValue();
            }
            Arrays.sort(mbeans);
            final DomainInfo domain = new DomainInfo(entry.getKey(), mbeans);
            this._domains[domainidx++] = domain;
        }
        Arrays.sort(this._domains);
    }

    private DomainInfo[] _domains;
}
