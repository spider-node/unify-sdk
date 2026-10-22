package com.unify.sdk.internal;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.unify.sdk.ErrorCode;
import com.unify.sdk.TransformException;
import com.unify.sdk.function.TransformFunction;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * DSL 编译器：把 DSL 的 JSON 树编译为 {@link Rule} 树。所有结构、路径、表达式、函数错误都在这里报出。
 *
 * <h2>DSL 结构</h2>
 * <pre>
 * {
 *   "name": "legacy-customer",          // 可选；预注册的映射必须有
 *   "description": "...",               // 可选
 *   "options": { "omitNulls": true },   // 可选
 *   "mapping": { ... }                  // 必填，输出模板
 * }
 * </pre>
 *
 * <h2>输出模板中的值</h2>
 * <ul>
 *     <li>{@code "$..."}：路径取值</li>
 *     <li>{@code "=..."}：表达式</li>
 *     <li>其他字符串、数字、布尔、null：常量</li>
 *     <li>数组：按位置逐个求值</li>
 *     <li>不含 {@code $} 开头字段的对象：按字段逐个求值</li>
 *     <li>含 {@code $} 开头字段的对象：指令，见 {@code $const / $switch / $each / $if}，
 *     均可附加 {@code $default}（{@code $const} 除外）</li>
 * </ul>
 */
public final class DslCompiler {

    private static final Set<String> ROOT_KEYS = Set.of("name", "description", "options", "mapping");
    private static final Set<String> OPTION_KEYS = Set.of("omitNulls");

    private static final String CONST = "$const";
    private static final String SWITCH = "$switch";
    private static final String CASES = "$cases";
    private static final String EACH = "$each";
    private static final String ITEM = "$item";
    private static final String FILTER = "$filter";
    private static final String IF = "$if";
    private static final String THEN = "$then";
    private static final String ELSE = "$else";
    private static final String DEFAULT = "$default";

    private static final Set<String> SWITCH_KEYS = Set.of(SWITCH, CASES, DEFAULT);
    private static final Set<String> EACH_KEYS = Set.of(EACH, ITEM, FILTER, DEFAULT);
    private static final Set<String> IF_KEYS = Set.of(IF, THEN, ELSE, DEFAULT);

    private final Map<String, TransformFunction> functions;

    public DslCompiler(Map<String, TransformFunction> functions) {
        this.functions = Map.copyOf(functions);
    }

    public MappingDefinition compile(JsonNode dsl) {
        if (dsl == null || !dsl.isObject()) {
            throw new TransformException(ErrorCode.INVALID_DSL, null, "", "DSL 必须是 JSON 对象", null);
        }
        String name = optionalText(dsl, "name", null);
        Session session = new Session(name);
        Iterator<String> keys = dsl.fieldNames();
        while (keys.hasNext()) {
            String key = keys.next();
            if (!ROOT_KEYS.contains(key)) {
                throw session.error("/" + escape(key), "未知的顶层字段 " + key + "，只支持 name、description、options、mapping");
            }
        }
        String description = optionalText(dsl, "description", name);
        session.omitNulls = readOptions(dsl.get("options"), session);
        JsonNode mapping = dsl.get("mapping");
        if (mapping == null) {
            throw session.error("", "缺少 mapping");
        }
        return new MappingDefinition(name, description, session.compile(mapping, "/mapping"));
    }

    private static String optionalText(JsonNode dsl, String field, String mappingName) {
        JsonNode node = dsl.get(field);
        if (node == null || node.isNull()) {
            return null;
        }
        if (!node.isTextual() || node.textValue().isBlank()) {
            throw new TransformException(ErrorCode.INVALID_DSL, mappingName, "/" + field, field + " 必须是非空字符串", null);
        }
        return node.textValue().strip();
    }

    private static boolean readOptions(JsonNode options, Session session) {
        if (options == null || options.isNull()) {
            return false;
        }
        if (!options.isObject()) {
            throw session.error("/options", "options 必须是 JSON 对象");
        }
        Iterator<String> keys = options.fieldNames();
        while (keys.hasNext()) {
            String key = keys.next();
            if (!OPTION_KEYS.contains(key)) {
                throw session.error("/options/" + escape(key), "未知的选项 " + key + "，只支持 omitNulls");
            }
        }
        JsonNode omitNulls = options.get("omitNulls");
        if (omitNulls == null || omitNulls.isNull()) {
            return false;
        }
        if (!omitNulls.isBoolean()) {
            throw session.error("/options/omitNulls", "omitNulls 必须是 true 或 false");
        }
        return omitNulls.booleanValue();
    }

    /** JSON Pointer 转义。 */
    static String escape(String key) {
        return key.replace("~", "~0").replace("/", "~1");
    }

    /** 单次编译的状态。 */
    private final class Session {

        private final String name;
        private boolean omitNulls;

        Session(String name) {
            this.name = name;
        }

        Rule compile(JsonNode node, String pointer) {
            if (node.isTextual()) {
                return compileText(node.textValue(), pointer);
            }
            if (node.isArray()) {
                List<Rule> items = new ArrayList<>(node.size());
                for (int i = 0; i < node.size(); i++) {
                    items.add(compile(node.get(i), pointer + "/" + i));
                }
                return new Rules.Arr(items);
            }
            if (node.isObject()) {
                return isDirective(node) ? compileDirective(node, pointer) : compileObject(node, pointer);
            }
            return new Rules.Const(node.deepCopy());
        }

        private Rule compileText(String text, String pointer) {
            if (text.startsWith("$")) {
                try {
                    return new Rules.Path(JsonPath.parse(text));
                } catch (IllegalArgumentException e) {
                    throw error(pointer, "路径 " + text + " 无效：" + e.getMessage());
                }
            }
            if (text.startsWith("=")) {
                return ExprParser.parse(text.substring(1), functions, name, pointer);
            }
            return new Rules.Const(TextNode.valueOf(text));
        }

        private Rule compileObject(JsonNode node, String pointer) {
            Map<String, Rule> fields = new LinkedHashMap<>();
            Iterator<Map.Entry<String, JsonNode>> it = node.fields();
            while (it.hasNext()) {
                Map.Entry<String, JsonNode> field = it.next();
                fields.put(field.getKey(), compile(field.getValue(), pointer + "/" + escape(field.getKey())));
            }
            return new Rules.Obj(fields, omitNulls);
        }

        private Rule compileDirective(JsonNode node, String pointer) {
            Set<String> keys = new LinkedHashSet<>();
            node.fieldNames().forEachRemaining(keys::add);
            for (String key : keys) {
                if (!key.startsWith("$")) {
                    throw error(pointer + "/" + escape(key),
                            "指令对象中不能出现普通字段 " + key + "（输出字段名不能以 $ 开头）");
                }
            }
            Rule rule;
            if (keys.contains(CONST)) {
                if (keys.size() != 1) {
                    throw error(pointer, "$const 不能与其他字段同时出现");
                }
                return new Rules.Const(node.get(CONST).deepCopy());
            } else if (keys.contains(SWITCH)) {
                checkKeys(keys, SWITCH_KEYS, SWITCH, pointer);
                rule = compileSwitch(node, pointer);
            } else if (keys.contains(EACH)) {
                checkKeys(keys, EACH_KEYS, EACH, pointer);
                rule = new Rules.Each(
                        compile(node.get(EACH), pointer + "/" + EACH),
                        optional(node, FILTER, pointer),
                        optional(node, ITEM, pointer),
                        omitNulls);
            } else if (keys.contains(IF)) {
                checkKeys(keys, IF_KEYS, IF, pointer);
                if (!keys.contains(THEN) && !keys.contains(ELSE)) {
                    throw error(pointer, "$if 至少需要 $then 或 $else 之一");
                }
                rule = new Rules.If(
                        compile(node.get(IF), pointer + "/" + IF),
                        optional(node, THEN, pointer),
                        optional(node, ELSE, pointer));
            } else {
                throw error(pointer, "未知的指令 " + String.join("、", keys) + "，支持 $const、$switch、$each、$if");
            }
            Rule fallback = optional(node, DEFAULT, pointer);
            return fallback == null ? rule : new Rules.Default(rule, fallback);
        }

        private Rule compileSwitch(JsonNode node, String pointer) {
            JsonNode cases = node.get(CASES);
            if (cases == null || !cases.isObject()) {
                throw error(pointer + "/" + CASES, "$switch 需要对象形式的 $cases，例如 {\"1\": \"ACTIVE\"}");
            }
            Map<String, Rule> compiled = new LinkedHashMap<>();
            Iterator<Map.Entry<String, JsonNode>> it = cases.fields();
            while (it.hasNext()) {
                Map.Entry<String, JsonNode> entry = it.next();
                compiled.put(entry.getKey(),
                        compile(entry.getValue(), pointer + "/" + CASES + "/" + escape(entry.getKey())));
            }
            return new Rules.Switch(compile(node.get(SWITCH), pointer + "/" + SWITCH), compiled);
        }

        private Rule optional(JsonNode node, String key, String pointer) {
            JsonNode value = node.get(key);
            return value == null ? null : compile(value, pointer + "/" + key);
        }

        private void checkKeys(Set<String> keys, Set<String> allowed, String directive, String pointer) {
            for (String key : keys) {
                if (!allowed.contains(key)) {
                    throw error(pointer + "/" + escape(key), "指令 " + directive + " 不支持 " + key
                            + "，可用字段：" + String.join("、", new java.util.TreeSet<>(allowed)));
                }
            }
        }

        private boolean isDirective(JsonNode node) {
            Iterator<String> keys = node.fieldNames();
            while (keys.hasNext()) {
                if (keys.next().startsWith("$")) {
                    return true;
                }
            }
            return false;
        }

        TransformException error(String pointer, String message) {
            return new TransformException(ErrorCode.INVALID_DSL, name, pointer, message, null);
        }
    }
}
