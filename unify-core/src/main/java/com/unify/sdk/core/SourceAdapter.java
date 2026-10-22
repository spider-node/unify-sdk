package com.unify.sdk.core;

import com.unify.sdk.api.Capability;

/**
 * 适配器：把一项统一能力适配到某一个内部数据源。
 * <p>
 * 每个"能力 × 内部数据源"组合对应一个适配器，负责：
 * <ol>
 *     <li>判断该数据源能否处理当前请求（{@link #supports}）；</li>
 *     <li>统一请求 → 内部请求（{@link #toSourceRequest}）；</li>
 *     <li>内部响应 → 统一响应（{@link #toUnifiedResponse}）。</li>
 * </ol>
 * 适配器应是无状态的，转换中出现的任何运行时异常都会被 SDK 包装为 {@code CONVERT_FAILED}。
 *
 * @param <Q>  统一请求类型
 * @param <R>  统一响应类型
 * @param <SQ> 内部请求类型
 * @param <SR> 内部响应类型
 */
public interface SourceAdapter<Q, R, SQ, SR> {

    /** 绑定的数据源 ID，需与 {@link com.unify.sdk.spi.DataSource#sourceId()} 一致。 */
    String sourceId();

    /** 适配的能力。 */
    Capability<Q, R> capability();

    /**
     * 该数据源能否处理此请求，例如按 ID 前缀、业务类型、租户判断。默认可处理所有请求。
     */
    default boolean supports(Q request, InvocationContext context) {
        return true;
    }

    /**
     * 优先级，数值越小越先尝试（顺序回退模式），或在合并时越靠前（并行合并模式）。
     */
    default int order() {
        return 0;
    }

    SQ toSourceRequest(Q request, InvocationContext context);

    /**
     * @param response 内部响应，数据源未查到数据时可能为 {@code null}
     * @param request  原始统一请求，便于补全响应字段
     */
    R toUnifiedResponse(SR response, Q request, InvocationContext context);
}
