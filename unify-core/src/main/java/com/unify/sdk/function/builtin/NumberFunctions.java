package com.unify.sdk.function.builtin;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.NullNode;
import com.unify.sdk.function.FunctionArgs;
import com.unify.sdk.function.TransformFunction;
import com.unify.sdk.internal.Nodes;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.Map;
import java.util.function.BinaryOperator;

import static com.unify.sdk.function.builtin.BuiltinFunctions.define;

/**
 * 数字函数。全部使用 {@link BigDecimal} 计算，不会出现浮点误差；参数接受数字或数字文本。
 * 结果没有小数部分时输出为整数，否则保留计算得到的小数位。
 */
final class NumberFunctions {

    private NumberFunctions() {
    }

    static void register(Map<String, TransformFunction> functions) {
        // toNumber(value)：数字原样返回，数字文本转为数字
        define(functions, "toNumber", 1, 1, args -> {
            JsonNode value = args.get(0);
            if (value.isNumber()) {
                return value;
            }
            return number(args.decimal(0));
        });
        // toInt(value)：截断小数部分（向 0 取整）
        define(functions, "toInt", 1, 1, args -> {
            BigDecimal value = args.decimal(0);
            if (value == null) {
                return NullNode.getInstance();
            }
            try {
                return Nodes.integral(value.setScale(0, RoundingMode.DOWN).longValueExact());
            } catch (ArithmeticException e) {
                throw args.error(0, "超出整数范围：" + value.toPlainString());
            }
        });
        // round(value[, scale])：四舍五入（HALF_UP），默认保留 0 位小数
        define(functions, "round", 1, 2, args -> {
            BigDecimal value = args.decimal(0);
            if (value == null) {
                return NullNode.getInstance();
            }
            Integer scale = args.integer(1);
            return number(value.setScale(scale == null ? 0 : scale, RoundingMode.HALF_UP));
        });
        // add(a, b, ...)：任一参数为空时结果为空
        define(functions, "add", 2, Integer.MAX_VALUE, args -> fold(args, BigDecimal::add));
        // subtract(a, b)：a - b
        define(functions, "subtract", 2, 2, args -> fold(args, BigDecimal::subtract));
        // multiply(a, b, ...)
        define(functions, "multiply", 2, Integer.MAX_VALUE, args -> fold(args, BigDecimal::multiply));
        // divide(a, b[, scale])：给出 scale 时四舍五入到该位数，否则保留 16 位有效数字并去掉末尾的 0；除数为 0 时报错
        define(functions, "divide", 2, 3, args -> {
            BigDecimal dividend = args.decimal(0);
            BigDecimal divisor = args.decimal(1);
            if (dividend == null || divisor == null) {
                return NullNode.getInstance();
            }
            if (divisor.signum() == 0) {
                throw args.error(1, "除数不能为 0");
            }
            Integer scale = args.integer(2);
            if (scale != null) {
                return number(dividend.divide(divisor, scale, RoundingMode.HALF_UP));
            }
            return number(dividend.divide(divisor, MathContext.DECIMAL64).stripTrailingZeros());
        });
        // centsToYuan(cents)：分转元，保留 2 位小数，例如 12345 → 123.45、100 → 1.00
        define(functions, "centsToYuan", 1, 1, args -> {
            BigDecimal cents = args.decimal(0);
            return cents == null ? NullNode.getInstance()
                    : Nodes.decimal(cents.movePointLeft(2).setScale(2, RoundingMode.HALF_UP));
        });
        // yuanToCents(yuan)：元转分，四舍五入为整数，例如 123.456 → 12346
        define(functions, "yuanToCents", 1, 1, args -> {
            BigDecimal yuan = args.decimal(0);
            if (yuan == null) {
                return NullNode.getInstance();
            }
            try {
                return Nodes.integral(yuan.movePointRight(2).setScale(0, RoundingMode.HALF_UP).longValueExact());
            } catch (ArithmeticException e) {
                throw args.error(0, "超出整数范围：" + yuan.toPlainString());
            }
        });
    }

    private static JsonNode fold(FunctionArgs args, BinaryOperator<BigDecimal> operator) {
        BigDecimal result = null;
        for (int i = 0; i < args.size(); i++) {
            BigDecimal value = args.decimal(i);
            if (value == null) {
                return NullNode.getInstance();
            }
            result = result == null ? value : operator.apply(result, value);
        }
        return number(result);
    }

    /** 没有小数部分且在 long 范围内时输出整数节点，否则输出小数节点。 */
    static JsonNode number(BigDecimal value) {
        if (value == null) {
            return NullNode.getInstance();
        }
        if (value.scale() <= 0) {
            try {
                return Nodes.integral(value.longValueExact());
            } catch (ArithmeticException e) {
                return Nodes.decimal(value);
            }
        }
        return Nodes.decimal(value);
    }
}
