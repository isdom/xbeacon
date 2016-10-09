package org.jocean.zkoss.ui;

import java.util.Map;

import org.jocean.zkoss.model.SimpleTreeModel;
import org.jocean.zkoss.model.SimpleTreeModel.Node;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;

public class JSON2TreeModel {
    public static SimpleTreeModel buildTree(final Object value) {
        return new SimpleTreeModel(buildNode(new Node(""), value));
    }
    
    private static Node buildNode(final Node parent, final Object value) {
        if (value instanceof JSONObject) {
            return buildNode4JSON(parent, (JSONObject)value);
        } else if (value instanceof JSONArray) {
            return buildNode4Array(parent, (JSONArray)value);
        } else {
            parent.setData(value);
        }
        
        return parent;
    }
    
    private static Node buildNode4JSON(final Node parent, final JSONObject jobj) {
        for (Map.Entry<String, Object> entry : jobj.entrySet()) {
            parent.addChild(buildNode(new Node(entry.getKey()), entry.getValue()));
        }
        return parent;
    }

    private static Node buildNode4Array(final Node parent, final JSONArray jarray) {
        for (int idx = 0; idx < jarray.size(); idx++) {
            parent.addChild(buildNode(new Node(""), jarray.get(idx)));
        }
        return parent;
    }
}
