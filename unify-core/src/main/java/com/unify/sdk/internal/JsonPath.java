package com.unify.sdk.internal;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.NullNode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 取值路径。
 *
 * <pre>
 * $                当前数据（顶层为源数据根节点，$each 内为当前元素）
 * $$               源数据根节点
 * $.a.b            对象字段
 * $['a b']         含特殊字符的字段名，单引号或双引号均可
 * $.list[0]        数组下标，负数从末尾计数，[-1] 为最后一个
 * $.list[*].name   投影：对数组每个元素取 name，结果为数组，空值会被去掉
 * $.obj.*          投影：对象的所有字段值
 * </pre>
 *
 * 路径不存在时结果为 JSON null，不会报错。
 */
public final class JsonPath {

    private final String expression;
    private final boolean fromRoot;
    private final List<Segment> segments;
    private final boolean projecting;

    private JsonPath(String expression, boolean fromRoot, List<Segment> segments) {
        this.expression = expression;
        this.fromRoot = fromRoot;
        this.segments = Collections.unmodifiableList(segments);
        this.projecting = segments.stream().anyMatch(segment -> segment instanceof Wildcard);
    }

    /**
     * 解析完整的路径文本。
     *
     * @throws IllegalArgumentException 路径语法错误
     */
    public static JsonPath parse(String text) {
        Parsed parsed = parse(text, 0);
        if (parsed.end() != text.length()) {
            throw new IllegalArgumentException("第 " + (parsed.end() + 1) + " 个字符起有多余内容：" + text.substring(parsed.end()));
        }
        return parsed.path();
    }

    /**
     * 从 {@code start} 开始尽可能长地解析一个路径，供表达式解析器使用。
     *
     * @throws IllegalArgumentException 路径语法错误
     */
    public static Parsed parse(String text, int start) {
        int length = text.length();
        if (start >= length || text.charAt(start) != '$') {
            throw new IllegalArgumentException("路径必须以 $ 开头");
        }
        int i = start + 1;
        boolean fromRoot = false;
        if (i < length && text.charAt(i) == '$') {
            fromRoot = true;
            i++;
        }
        List<Segment> segments = new ArrayList<>();
        while (i < length) {
            char c = text.charAt(i);
            if (c == '.') {
                i++;
                if (i < length && text.charAt(i) == '*') {
                    segments.add(Wildcard.INSTANCE);
                    i++;
                    continue;
                }
                int nameStart = i;
                while (i < length && isNameChar(text.charAt(i))) {
                    i++;
                }
                if (i == nameStart) {
                    throw new IllegalArgumentException("第 " + (nameStart + 1) + " 个字符：'.' 后缺少字段名");
                }
                segments.add(new Field(text.substring(nameStart, i)));
            } else if (c == '[') {
                i = parseBracket(text, i + 1, segments);
            } else {
                break;
            }
        }
        return new Parsed(new JsonPath(text.substring(start, i), fromRoot, segments), i);
    }

    private static int parseBracket(String text, int i, List<Segment> segments) {
        int length = text.length();
        if (i >= length) {
            throw new IllegalArgumentException("'[' 未闭合");
        }
        char c = text.charAt(i);
        if (c == '*') {
            segments.add(Wildcard.INSTANCE);
            return expectClose(text, i + 1);
        }
        if (c == '\'' || c == '"') {
            StringBuilder name = new StringBuilder();
            int j = i + 1;
            while (true) {
                if (j >= length) {
                    throw new IllegalArgumentException("字段名的引号未闭合");
                }
                char ch = text.charAt(j);
                if (ch == '\\' && j + 1 < length) {
                    name.append(text.charAt(j + 1));
                    j += 2;
                } else if (ch == c) {
                    break;
                } else {
                    name.append(ch);
                    j++;
                }
            }
            segments.add(new Field(name.toString()));
            return expectClose(text, j + 1);
        }
        int j = i;
        if (j < length && text.charAt(j) == '-') {
            j++;
        }
        int digitsStart = j;
        while (j < length && Character.isDigit(text.charAt(j))) {
            j++;
        }
        if (j == digitsStart) {
            throw new IllegalArgumentException("'[' 后应为下标、* 或带引号的字段名");
        }
        try {
            segments.add(new Index(Integer.parseInt(text.substring(i, j))));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("下标超出范围：" + text.substring(i, j));
        }
        return expectClose(text, j);
    }

    private static int expectClose(String text, int i) {
        if (i >= text.length() || text.charAt(i) != ']') {
            throw new IllegalArgumentException("缺少 ']'");
        }
        return i + 1;
    }

    private static boolean isNameChar(char c) {
        return Character.isLetterOrDigit(c) || c == '_' || c == '-';
    }

    public JsonNode evaluate(JsonNode root, JsonNode current) {
        JsonNode start = fromRoot ? root : current;
        if (!projecting) {
            JsonNode node = start;
            for (Segment segment : segments) {
                node = step(node, segment);
                if (node == null) {
                    return NullNode.getInstance();
                }
            }
            return Nodes.orNull(node);
        }
        List<JsonNode> nodes = new ArrayList<>();
        nodes.add(start);
        for (Segment segment : segments) {
            List<JsonNode> next = new ArrayList<>();
            for (JsonNode node : nodes) {
                if (segment instanceof Wildcard) {
                    if (node != null && node.isContainerNode()) {
                        node.forEach(next::add);
                    }
                } else {
                    next.add(step(node, segment));
                }
            }
            next.removeIf(Nodes::isNull);
            nodes = next;
        }
        ArrayNode result = JsonNodeFactory.instance.arrayNode(nodes.size());
        nodes.forEach(result::add);
        return result;
    }

    private static JsonNode step(JsonNode node, Segment segment) {
        if (Nodes.isNull(node)) {
            return null;
        }
        if (segment instanceof Field field) {
            return node.isObject() ? node.get(field.name()) : null;
        }
        if (segment instanceof Index index && node.isArray()) {
            int i = index.value() < 0 ? node.size() + index.value() : index.value();
            return i >= 0 && i < node.size() ? node.get(i) : null;
        }
        return null;
    }

    public String expression() {
        return expression;
    }

    @Override
    public String toString() {
        return expression;
    }

    /** 解析结果与结束位置（不含）。 */
    public record Parsed(JsonPath path, int end) {
    }

    private sealed interface Segment permits Field, Index, Wildcard {
    }

    private record Field(String name) implements Segment {
    }

    private record Index(int value) implements Segment {
    }

    private enum Wildcard implements Segment {
        INSTANCE
    }
}
