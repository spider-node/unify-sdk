package com.unify.sdk.spring;

import com.unify.sdk.JsonTransformer;

/**
 * 在自动装配构建 {@link JsonTransformer} 之前调整 Builder，例如替换 ObjectMapper、注册额外的映射或函数。
 * 多个实现按 {@link org.springframework.core.Ordered} / {@code @Order} 顺序执行，且在函数 Bean 与映射文件之后执行。
 */
@FunctionalInterface
public interface TransformerCustomizer {

    void customize(JsonTransformer.Builder builder);
}
