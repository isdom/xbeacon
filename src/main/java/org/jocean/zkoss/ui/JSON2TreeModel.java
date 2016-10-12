package org.jocean.zkoss.ui;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.jocean.zkoss.model.SimpleTreeModel;
import org.jocean.zkoss.model.SimpleTreeModel.Node;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.google.common.collect.Lists;

public class JSON2TreeModel {
    final private static Comparator<Map.Entry<String, Object>> COMPARATOR_NODE = 
    new Comparator<Map.Entry<String, Object>>() {
        @Override
        public int compare(final Entry<String, Object> o1,
                final Entry<String, Object> o2) {
            return o1.getKey().compareTo(o2.getKey());
        }};
        
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
        final List<Map.Entry<String, Object>> nodes = 
                Lists.newArrayList(jobj.entrySet());
        Collections.sort(nodes, COMPARATOR_NODE);
        
        for (Map.Entry<String, Object> node : nodes) {
            parent.addChild(buildNode(new Node(node.getKey()), node.getValue()));
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
