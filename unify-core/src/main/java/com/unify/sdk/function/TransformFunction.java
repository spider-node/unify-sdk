package com.unify.sdk.function;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * DSL 函数，也是 SDK 的代码扩展点：内置函数满足不了的复杂转换，实现本接口后注册即可在表达式中调用。
 *
 * <pre>{@code
 * JsonTransformer transformer = JsonTransformer.builder()
 *         .function("maskPhone", TransformFunction.of(1, 1, args -> {
 *             String phone = args.text(0);
 *             return phone == null ? null : args.nodes().textNode(phone.replaceAll("(\\d{3})\\d{4}(\\d{4})", "$1****$2"));
 *         }))
 *         .build();
 * // DSL 中使用："phone": "=maskPhone($.mobile)"
 * }</pre>
 *
 * <h2>实现约定</h2>
 * <ul>
 *     <li>必须线程安全、无副作用，同一实例会被并发调用。</li>
 *     <li>返回 {@code null} 等同于返回 JSON null。</li>
 *     <li>源数据不符合预期时直接抛出异常（推荐 {@link FunctionArgs#error(int, String)}），
 *     SDK 会包装为 {@code FUNCTION_FAILED} 并附上 DSL 中的出错位置。</li>
 *     <li>不要修改参数节点，它们可能直接引用源数据。</li>
 * </ul>
 */
@FunctionalInterface
public interface TransformFunction {

    JsonNode apply(FunctionArgs args);

    /** 最少参数个数，编译 DSL 时校验。 */
    default int minArgs() {
        return 0;
    }

    /** 最多参数个数，编译 DSL 时校验。 */
    default int maxArgs() {
        return Integer.MAX_VALUE;
    }

    /** 为 lambda 实现附加参数个数约束。 */
    static TransformFunction of(int minArgs, int maxArgs, TransformFunction body) {
        if (minArgs < 0 || maxArgs < minArgs) {
            throw new IllegalArgumentException("参数个数范围无效：" + minArgs + " ~ " + maxArgs);
        }
        if (body == null) {
            throw new IllegalArgumentException("函数实现不能为空");
        }
        return new TransformFunction() {
            @Override
            public JsonNode apply(FunctionArgs args) {
                return body.apply(args);
            }

            @Override
            public int minArgs() {
                return minArgs;
            }

            @Override
            public int maxArgs() {
                return maxArgs;
            }
        };
    }
}
