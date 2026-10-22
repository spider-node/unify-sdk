package com.unify.sdk.spring;

import com.unify.sdk.core.UnifiedClientBuilder;

/**
 * 在自动装配构建 {@link com.unify.sdk.api.UnifiedClient} 之前对构建器做额外定制，
 * 例如指定线程池、手工注册非 Spring 管理的数据源等。多个定制器按 {@code @Order} 顺序执行。
 */
@FunctionalInterface
public interface UnifiedClientCustomizer {

    void customize(UnifiedClientBuilder builder);
}
