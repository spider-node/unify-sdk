package com.unify.sdk.function.builtin;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.BooleanNode;
import com.fasterxml.jackson.databind.node.NullNode;
import com.unify.sdk.function.FunctionArgs;
import com.unify.sdk.function.TransformFunction;
import com.unify.sdk.internal.Nodes;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.IntPredicate;

import static com.unify.sdk.function.builtin.BuiltinFunctions.define;

/** 逻辑函数。条件判断使用 {@link Nodes#truthy(JsonNode)} 的真值规则，相等比较使用 {@link Nodes#sameValue}。 */
final class LogicFunctions {

    private LogicFunctions() {
    }

    static void register(Map<String, TransformFunction> functions) {
        // default(value, fallback)：value 为空时返回 fallback
        define(functions, "default", 2, 2, args -> args.isNull(0) ? args.get(1) : args.get(0));
        // coalesce(a, b, ...)：第一个非空值
        define(functions, "coalesce", 1, Integer.MAX_VALUE, args -> {
            for (JsonNode value : args.values()) {
                if (!Nodes.isNull(value)) {
                    return value;
                }
            }
            return NullNode.getInstance();
        });
        define(functions, "isNull", 1, 1, args -> BooleanNode.valueOf(args.isNull(0)));
        // isEmpty(value)：空值、空白文本、空数组、空对象
        define(functions, "isEmpty", 1, 1, args -> BooleanNode.valueOf(Nodes.isEmpty(args.get(0))));
        // eq(a, b)：数字按数值比较（1 等于 1.0），其余按文本或结构比较；两边都为空视为相等
        define(functions, "eq", 2, 2, args -> BooleanNode.valueOf(Nodes.sameValue(args.get(0), args.get(1))));
        define(functions, "ne", 2, 2, args -> BooleanNode.valueOf(!Nodes.sameValue(args.get(0), args.get(1))));
        // gt/gte/lt/lte(a, b)：按数值比较，接受数字文本；任一方为空时为 false
        define(functions, "gt", 2, 2, args -> compare(args, c -> c > 0));
        define(functions, "gte", 2, 2, args -> compare(args, c -> c >= 0));
        define(functions, "lt", 2, 2, args -> compare(args, c -> c < 0));
        define(functions, "lte", 2, 2, args -> compare(args, c -> c <= 0));
        define(functions, "and", 1, Integer.MAX_VALUE, args -> {
            for (JsonNode value : args.values()) {
                if (!Nodes.truthy(value)) {
                    return BooleanNode.FALSE;
                }
            }
            return BooleanNode.TRUE;
        });
        define(functions, "or", 1, Integer.MAX_VALUE, args -> {
            for (JsonNode value : args.values()) {
                if (Nodes.truthy(value)) {
                    return BooleanNode.TRUE;
                }
            }
            return BooleanNode.FALSE;
        });
        define(functions, "not", 1, 1, args -> BooleanNode.valueOf(!Nodes.truthy(args.get(0))));
        // in(value, c1, c2, ...)：value 是否等于任一候选值；只有一个候选且为数组时，在数组元素中查找
        define(functions, "in", 2, Integer.MAX_VALUE, args -> {
            JsonNode value = args.get(0);
            List<JsonNode> candidates = new ArrayList<>(args.values().subList(1, args.size()));
            if (candidates.size() == 1 && candidates.get(0).isArray()) {
                candidates = args.list(1);
            }
            for (JsonNode candidate : candidates) {
                if (Nodes.sameValue(value, candidate)) {
                    return BooleanNode.TRUE;
                }
            }
            return BooleanNode.FALSE;
        });
        // if(condition, then[, else])
        define(functions, "if", 2, 3, args -> Nodes.truthy(args.get(0)) ? args.get(1) : args.get(2));
        // toBoolean(value)：true/1/y/yes/on 为真，false/0/n/no/off 为假（不区分大小写），数字非 0 为真
        define(functions, "toBoolean", 1, 1, args -> Nodes.bool(args.bool(0)));
    }

    private static JsonNode compare(FunctionArgs args, IntPredicate test) {
        BigDecimal left = args.decimal(0);
        BigDecimal right = args.decimal(1);
        if (left == null || right == null) {
            return BooleanNode.FALSE;
        }
        return BooleanNode.valueOf(test.test(left.compareTo(right)));
    }
}
