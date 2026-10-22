package com.unify.sdk.api;

import java.time.Duration;

/**
 * 一次内部数据源调用的轨迹。
 *
 * @param sourceId     数据源 ID
 * @param status       调用状态
 * @param elapsed      耗时（含请求、响应转换）
 * @param errorCode    失败时的错误码，成功时为 {@code null}
 * @param errorMessage 失败时的错误描述，成功时为 {@code null}
 */
public record SourceTrace(String sourceId, SourceStatus status, Duration elapsed, ErrorCode errorCode, String errorMessage) {

    public static SourceTrace success(String sourceId, Duration elapsed) {
        return new SourceTrace(sourceId, SourceStatus.SUCCESS, elapsed, null, null);
    }

    public boolean isSuccess() {
        return status == SourceStatus.SUCCESS;
    }
}
