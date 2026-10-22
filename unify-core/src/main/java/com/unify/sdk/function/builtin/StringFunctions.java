package com.unify.sdk.function.builtin;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.NullNode;
import com.unify.sdk.function.TransformFunction;
import com.unify.sdk.internal.Nodes;

import java.util.List;
import java.util.Locale;
import java.util.Map;

import static com.unify.sdk.function.builtin.BuiltinFunctions.define;

/** 文本函数。 */
final class StringFunctions {

    private StringFunctions() {
    }

    static void register(Map<String, TransformFunction> functions) {
        // trim(text)：去掉首尾空白
        define(functions, "trim", 1, 1, args -> {
            String text = args.text(0);
            return Nodes.textNode(text == null ? null : text.strip());
        });
        // trimToNull(text)：去掉首尾空白，结果为空字符串时返回 null
        define(functions, "trimToNull", 1, 1, args -> {
            String text = args.text(0);
            String trimmed = text == null ? null : text.strip();
            return Nodes.textNode(trimmed == null || trimmed.isEmpty() ? null : trimmed);
        });
        define(functions, "upper", 1, 1, args -> {
            String text = args.text(0);
            return Nodes.textNode(text == null ? null : text.toUpperCase(Locale.ROOT));
        });
        define(functions, "lower", 1, 1, args -> {
            String text = args.text(0);
            return Nodes.textNode(text == null ? null : text.toLowerCase(Locale.ROOT));
        });
        // concat(a, b, ...)：拼接，跳过空值；全部为空时返回 null
        define(functions, "concat", 1, Integer.MAX_VALUE, args -> {
            StringBuilder out = new StringBuilder();
            boolean any = false;
            for (int i = 0; i < args.size(); i++) {
                String text = args.text(i);
                if (text != null) {
                    out.append(text);
                    any = true;
                }
            }
            return any ? Nodes.textNode(out.toString()) : NullNode.getInstance();
        });
        // substring(text, start[, end])：下标从 0 开始，越界时自动截断到合法范围
        define(functions, "substring", 2, 3, args -> {
            String text = args.text(0);
            if (text == null) {
                return NullNode.getInstance();
            }
            Integer startArg = args.integer(1);
            Integer endArg = args.integer(2);
            int length = text.length();
            int start = clamp(startArg == null ? 0 : startArg, length);
            int end = clamp(endArg == null ? length : endArg, length);
            return Nodes.textNode(end <= start ? "" : text.substring(start, end));
        });
        // replace(text, target, replacement)：按字面量全部替换，不使用正则
        define(functions, "replace", 3, 3, args -> {
            String text = args.text(0);
            if (text == null) {
                return NullNode.getInstance();
            }
            String target = args.text(1);
            if (target == null || target.isEmpty()) {
                return Nodes.textNode(text);
            }
            String replacement = args.text(2);
            return Nodes.textNode(text.replace(target, replacement == null ? "" : replacement));
        });
        // split(text, separator)：按字面量分隔，每段去掉首尾空白，丢弃空段
        define(functions, "split", 2, 2, args -> {
            String text = args.text(0);
            if (text == null) {
                return NullNode.getInstance();
            }
            String separator = args.text(1);
            if (separator == null || separator.isEmpty()) {
                throw args.error(1, "分隔符不能为空");
            }
            ArrayNode out = JsonNodeFactory.instance.arrayNode();
            int from = 0;
            while (true) {
                int index = text.indexOf(separator, from);
                String part = (index < 0 ? text.substring(from) : text.substring(from, index)).strip();
                if (!part.isEmpty()) {
                    out.add(part);
                }
                if (index < 0) {
                    break;
                }
                from = index + separator.length();
            }
            return out;
        });
        // join(list, separator)：用分隔符连接数组元素，跳过空值
        define(functions, "join", 2, 2, args -> {
            List<JsonNode> items = args.list(0);
            if (items == null) {
                return NullNode.getInstance();
            }
            String separator = args.text(1);
            StringBuilder out = new StringBuilder();
            boolean first = true;
            for (JsonNode item : items) {
                if (Nodes.isNull(item)) {
                    continue;
                }
                if (item.isContainerNode()) {
                    throw args.error(0, "的元素不能是数组或对象");
                }
                if (!first && separator != null) {
                    out.append(separator);
                }
                out.append(Nodes.text(item));
                first = false;
            }
            return Nodes.textNode(out.toString());
        });
        // toString(value)：转为文本；数组与对象转为 JSON 文本
        define(functions, "toString", 1, 1, args -> Nodes.textNode(Nodes.text(args.get(0))));
    }

    private static int clamp(int index, int length) {
        return Math.max(0, Math.min(index, length));
    }
}
