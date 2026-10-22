package com.unify.sdk.function.builtin;

import com.unify.sdk.function.FunctionProvider;
import com.unify.sdk.function.TransformFunction;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 内置函数，{@link com.unify.sdk.JsonTransformer} 总会加载它们，自定义函数不能与之重名。
 *
 * <h2>文本</h2>
 * trim、trimToNull、upper、lower、concat、substring、replace、split、join、toString
 *
 * <h2>逻辑</h2>
 * default、coalesce、isNull、isEmpty、eq、ne、gt、gte、lt、lte、and、or、not、in、if、toBoolean
 *
 * <h2>数字</h2>
 * toNumber、toInt、round、add、subtract、multiply、divide、centsToYuan、yuanToCents
 *
 * <h2>日期时间</h2>
 * parseDateTime、parseDate、formatDateTime、epochMillisToDateTime、epochSecondsToDateTime、dateTimeToEpochMillis
 *
 * <h2>集合</h2>
 * size、first、last、distinct、compact、flatten、contains
 *
 * <p>约定：除特别说明外，参数为空时结果为空，不会报错；参数类型不对时报错。各函数的详细说明见对应的实现类。
 */
public final class BuiltinFunctions implements FunctionProvider {

    private static final Map<String, TransformFunction> FUNCTIONS = create();

    @Override
    public Map<String, TransformFunction> functions() {
        return FUNCTIONS;
    }

    private static Map<String, TransformFunction> create() {
        Map<String, TransformFunction> functions = new LinkedHashMap<>();
        StringFunctions.register(functions);
        LogicFunctions.register(functions);
        NumberFunctions.register(functions);
        DateFunctions.register(functions);
        CollectionFunctions.register(functions);
        return Collections.unmodifiableMap(functions);
    }

    static void define(Map<String, TransformFunction> functions, String name, int minArgs, int maxArgs,
            TransformFunction body) {
        if (functions.putIfAbsent(name, TransformFunction.of(minArgs, maxArgs, body)) != null) {
            throw new IllegalStateException("内置函数重复定义：" + name);
        }
    }
}
