package org.jocean.xbeacon.jmxui;

import java.util.Map;

import org.jocean.jolokia.spi.ListResponse.ArgInfo;
import org.jocean.zkoss.annotation.RowSource;
import org.zkoss.zul.Doublebox;
import org.zkoss.zul.Intbox;
import org.zkoss.zul.Longbox;
import org.zkoss.zul.Textbox;
import org.zkoss.zul.impl.InputElement;

import com.google.common.collect.Maps;

public class ArgUI {
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

    public ArgUI(final ArgInfo arg) {
        this._name = arg.getName();
        this._description = arg.getDescription();
        this._type = arg.getType();
        this._input = type2input(this._type);
        this._inputComponent = this._input.newInputComponent();
    }

    public Object getInputObject() {
        return this._input.getInputObject(_inputComponent);
    }

    public String getType() {
        return this._type;
    }

    @RowSource(name="描述")
    private final String _description;

    @RowSource(name="参数")
    private final String _name;

    @RowSource(name="类型")
    private final String _type;

    @RowSource(name="输入值")
    private final InputElement _inputComponent;

    private final Input _input;

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
