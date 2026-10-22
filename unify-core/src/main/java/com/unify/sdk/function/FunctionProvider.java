package com.unify.sdk.function;

import java.util.Map;

/**
 * 批量提供函数，适合把一组业务函数打包成函数库。
 * <p>
 * 可以通过 {@code JsonTransformer.Builder#functions(FunctionProvider)} 显式注册，
 * 也可以在 {@code META-INF/services/com.unify.sdk.function.FunctionProvider} 中声明，
 * 再调用 {@code JsonTransformer.Builder#discoverFunctions()} 自动发现。
 */
public interface FunctionProvider {

    /** 函数名 → 函数。函数名只能包含字母、数字、下划线，且不能以数字开头。 */
    Map<String, TransformFunction> functions();
}
