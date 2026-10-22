package com.unify.sdk.function.builtin;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.BooleanNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.NullNode;
import com.unify.sdk.function.TransformFunction;
import com.unify.sdk.internal.Nodes;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.unify.sdk.function.builtin.BuiltinFunctions.define;

/** 集合函数。参数不是数组时视为只有一个元素的数组。 */
final class CollectionFunctions {

    private CollectionFunctions() {
    }

    static void register(Map<String, TransformFunction> functions) {
        // size(value)：数组元素个数、对象字段个数或文本长度；空值为 0
        define(functions, "size", 1, 1, args -> {
            JsonNode value = args.get(0);
            if (Nodes.isNull(value)) {
                return Nodes.integral(0L);
            }
            if (value.isContainerNode()) {
                return Nodes.integral((long) value.size());
            }
            if (value.isTextual()) {
                return Nodes.integral((long) value.textValue().length());
            }
            throw args.error(0, "应为数组、对象或文本，实际为 " + Nodes.typeName(value));
        });
        define(functions, "first", 1, 1, args -> {
            List<JsonNode> items = args.list(0);
            return items == null || items.isEmpty() ? NullNode.getInstance() : items.get(0);
        });
        define(functions, "last", 1, 1, args -> {
            List<JsonNode> items = args.list(0);
            return items == null || items.isEmpty() ? NullNode.getInstance() : items.get(items.size() - 1);
        });
        // distinct(list)：去重并保持原有顺序；数字按数值比较（1 与 1.0 视为相同）
        define(functions, "distinct", 1, 1, args -> {
            List<JsonNode> items = args.list(0);
            if (items == null) {
                return NullNode.getInstance();
            }
            Set<Object> seen = new HashSet<>();
            ArrayNode out = JsonNodeFactory.instance.arrayNode();
            for (JsonNode item : items) {
                if (seen.add(identity(item))) {
                    out.add(item);
                }
            }
            return out;
        });
        // compact(list)：去掉空值、空白文本、空数组、空对象
        define(functions, "compact", 1, 1, args -> {
            List<JsonNode> items = args.list(0);
            if (items == null) {
                return NullNode.getInstance();
            }
            ArrayNode out = JsonNodeFactory.instance.arrayNode();
            for (JsonNode item : items) {
                if (!Nodes.isEmpty(item)) {
                    out.add(item);
                }
            }
            return out;
        });
        // flatten(list)：展开一层嵌套数组
        define(functions, "flatten", 1, 1, args -> {
            List<JsonNode> items = args.list(0);
            if (items == null) {
                return NullNode.getInstance();
            }
            ArrayNode out = JsonNodeFactory.instance.arrayNode();
            for (JsonNode item : items) {
                if (item.isArray()) {
                    item.forEach(out::add);
                } else {
                    out.add(item);
                }
            }
            return out;
        });
        // contains(container, value)：数组是否包含元素、文本是否包含子串、对象是否包含字段；容器为空时为 false
        define(functions, "contains", 2, 2, args -> {
            JsonNode container = args.get(0);
            JsonNode value = args.get(1);
            if (Nodes.isNull(container)) {
                return BooleanNode.FALSE;
            }
            if (container.isArray()) {
                for (JsonNode item : container) {
                    if (Nodes.sameValue(item, value)) {
                        return BooleanNode.TRUE;
                    }
                }
                return BooleanNode.FALSE;
            }
            String text = args.text(1);
            if (text == null) {
                return BooleanNode.FALSE;
            }
            if (container.isObject()) {
                return BooleanNode.valueOf(container.has(text));
            }
            return BooleanNode.valueOf(Nodes.text(container).contains(text));
        });
    }

    /** 去重用的标识：数字按数值，其他标量按类型加文本，容器按结构。 */
    private static Object identity(JsonNode node) {
        if (Nodes.isNull(node)) {
            return "null";
        }
        if (node.isContainerNode()) {
            return node;
        }
        if (node.isNumber()) {
            return "number:" + Nodes.key(node);
        }
        return node.getNodeType() + ":" + Nodes.text(node);
    }
}
