package com.unify.sdk.api;

/**
 * 统一客户端，调用方使用 SDK 的唯一入口。
 * <p>
 * 实现必须线程安全，建议在应用内单例使用，应用关闭时调用 {@link #close()} 释放资源。
 */
public interface UnifiedClient extends AutoCloseable {

    /**
     * 执行一项能力，直接返回统一响应。
     *
     * @throws UnifiedException 所有内部接口均不可用，或请求非法时抛出
     */
    default <Q, R> R execute(Capability<Q, R> capability, Q request) {
        return execute(capability, request, InvokeOptions.defaults());
    }

    default <Q, R> R execute(Capability<Q, R> capability, Q request, InvokeOptions options) {
        return executeWithMeta(capability, request, options).data();
    }

    default <Q, R> UnifiedResult<R> executeWithMeta(Capability<Q, R> capability, Q request) {
        return executeWithMeta(capability, request, InvokeOptions.defaults());
    }

    /**
     * 执行一项能力，返回统一响应及调用元数据（各内部接口的调用轨迹、是否降级）。
     * 一般业务无需关心元数据，仅在排查问题或需要感知降级时使用。
     */
    <Q, R> UnifiedResult<R> executeWithMeta(Capability<Q, R> capability, Q request, InvokeOptions options);

    @Override
    void close();
}
