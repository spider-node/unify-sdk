package com.unify.sdk.internal;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.unify.sdk.ErrorCode;
import com.unify.sdk.TransformException;
import com.unify.sdk.function.FunctionArgs;
import com.unify.sdk.function.TransformFunction;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 各类规则的实现。规则在编译期创建，运行期只读。
 */
public final class Rules {

    private Rules() {
    }

    /** 常量。 */
    public record Const(JsonNode value) implements Rule {

        @Override
        public JsonNode evaluate(EvalContext context) {
            return value;
        }
    }

    /** 路径取值。 */
    public record Path(JsonPath path) implements Rule {

        @Override
        public JsonNode evaluate(EvalContext context) {
            return path.evaluate(context.root(), context.current());
        }
    }

    /** 函数调用。参数先求值，再调用函数；函数抛出的异常包装为 FUNCTION_FAILED 并附带 DSL 位置。 */
    public static final class Call implements Rule {

        private final String name;
        private final TransformFunction function;
        private final List<Rule> args;
        private final String location;

        public Call(String name, TransformFunction function, List<Rule> args, String location) {
            this.name = name;
            this.function = function;
            this.args = List.copyOf(args);
            this.location = location;
        }

        @Override
        public JsonNode evaluate(EvalContext context) {
            List<JsonNode> values = new ArrayList<>(args.size());
            for (Rule arg : args) {
                values.add(arg.evaluate(context));
            }
            try {
                return Nodes.orNull(function.apply(new FunctionArgs(name, values)));
            } catch (TransformException e) {
                throw e;
            } catch (RuntimeException e) {
                String detail = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
                throw new TransformException(ErrorCode.FUNCTION_FAILED, context.mappingName(), location,
                        "函数 " + name + " 执行失败：" + detail, e);
            }
        }
    }

    /** 对象模板：逐个字段求值。 */
    public static final class Obj implements Rule {

        private final Map<String, Rule> fields;
        private final boolean omitNulls;

        public Obj(Map<String, Rule> fields, boolean omitNulls) {
            this.fields = Collections.unmodifiableMap(new LinkedHashMap<>(fields));
            this.omitNulls = omitNulls;
        }

        @Override
        public JsonNode evaluate(EvalContext context) {
            ObjectNode out = JsonNodeFactory.instance.objectNode();
            for (Map.Entry<String, Rule> field : fields.entrySet()) {
                JsonNode value = field.getValue().evaluate(context);
                if (!Nodes.isNull(value)) {
                    out.set(field.getKey(), value);
                } else if (!omitNulls) {
                    out.putNull(field.getKey());
                }
            }
            return out;
        }
    }

    /** 数组模板：按位置逐个求值，空值保留为 null 以保持位置不变。 */
    public static final class Arr implements Rule {

        private final List<Rule> items;

        public Arr(List<Rule> items) {
            this.items = List.copyOf(items);
        }

        @Override
        public JsonNode evaluate(EvalContext context) {
            ArrayNode out = JsonNodeFactory.instance.arrayNode(items.size());
            for (Rule item : items) {
                out.add(Nodes.orNull(item.evaluate(context)));
            }
            return out;
        }
    }

    /**
     * {@code $each}：逐个元素转换。源为空时结果为空；源不是数组时视为只有一个元素的数组。
     * {@code $filter} 为假的元素被跳过；未配置 {@code $item} 时原样输出元素。
     */
    public static final class Each implements Rule {

        private final Rule source;
        private final Rule filter;
        private final Rule item;
        private final boolean omitNulls;

        public Each(Rule source, Rule filter, Rule item, boolean omitNulls) {
            this.source = source;
            this.filter = filter;
            this.item = item;
            this.omitNulls = omitNulls;
        }

        @Override
        public JsonNode evaluate(EvalContext context) {
            JsonNode input = source.evaluate(context);
            if (Nodes.isNull(input)) {
                return NullNode.getInstance();
            }
            Iterable<JsonNode> elements = input.isArray() ? input : List.of(input);
            ArrayNode out = JsonNodeFactory.instance.arrayNode();
            for (JsonNode element : elements) {
                EvalContext inner = context.withCurrent(element);
                if (filter != null && !Nodes.truthy(filter.evaluate(inner))) {
                    continue;
                }
                JsonNode value = item == null ? inner.current() : item.evaluate(inner);
                if (!Nodes.isNull(value)) {
                    out.add(value);
                } else if (!omitNulls) {
                    out.addNull();
                }
            }
            return out;
        }
    }

    /** {@code $if}：条件按真值规则判断，缺少的分支结果为空。 */
    public static final class If implements Rule {

        private final Rule condition;
        private final Rule then;
        private final Rule otherwise;

        public If(Rule condition, Rule then, Rule otherwise) {
            this.condition = condition;
            this.then = then;
            this.otherwise = otherwise;
        }

        @Override
        public JsonNode evaluate(EvalContext context) {
            Rule branch = Nodes.truthy(condition.evaluate(context)) ? then : otherwise;
            return branch == null ? NullNode.getInstance() : branch.evaluate(context);
        }
    }

    /** {@code $switch}：按值的规范化文本（见 {@link Nodes#key(JsonNode)}）匹配分支，未匹配时结果为空。 */
    public static final class Switch implements Rule {

        private final Rule value;
        private final Map<String, Rule> cases;

        public Switch(Rule value, Map<String, Rule> cases) {
            this.value = value;
            this.cases = Map.copyOf(cases);
        }

        @Override
        public JsonNode evaluate(EvalContext context) {
            String key = Nodes.key(value.evaluate(context));
            Rule matched = key == null ? null : cases.get(key);
            return matched == null ? NullNode.getInstance() : matched.evaluate(context);
        }
    }

    /** {@code $default}：被包装的规则结果为空时使用兜底值。 */
    public static final class Default implements Rule {

        private final Rule rule;
        private final Rule fallback;

        public Default(Rule rule, Rule fallback) {
            this.rule = rule;
            this.fallback = fallback;
        }

        @Override
        public JsonNode evaluate(EvalContext context) {
            JsonNode value = rule.evaluate(context);
            return Nodes.isNull(value) ? fallback.evaluate(context) : value;
        }
    }
}
