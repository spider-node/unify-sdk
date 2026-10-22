package com.unify.sdk.core;

import com.unify.sdk.api.SourceTrace;
import com.unify.sdk.api.UnifiedException;
import com.unify.sdk.api.UnifiedResult;

/**
 * 调用拦截器，用于日志、监控、审计等横切逻辑。
 * <p>
 * 拦截器抛出的异常会被 SDK 记录并忽略，不会影响主流程。
 */
public interface InvokeInterceptor {

    default void beforeInvoke(InvocationContext context, Object request) {
    }

    /** 每个数据源调用结束（成功、失败、超时、跳过）后触发。 */
    default void onSourceComplete(InvocationContext context, SourceTrace trace) {
    }

    default void afterInvoke(InvocationContext context, UnifiedResult<?> result) {
    }

    default void onError(InvocationContext context, UnifiedException error) {
    }
}
