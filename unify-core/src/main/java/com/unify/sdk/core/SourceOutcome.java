package com.unify.sdk.core;

import com.unify.sdk.api.ErrorCode;
import com.unify.sdk.api.SourceStatus;
import com.unify.sdk.api.SourceTrace;

import java.time.Duration;

/**
 * 单个数据源调用的结果。
 */
record SourceOutcome<R>(String sourceId, SourceStatus status, R data, Duration elapsed,
                        ErrorCode errorCode, String message, Throwable cause) {

    static <R> SourceOutcome<R> success(String sourceId, R data, Duration elapsed) {
        return new SourceOutcome<>(sourceId, SourceStatus.SUCCESS, data, elapsed, null, null, null);
    }

    static <R> SourceOutcome<R> failure(String sourceId, SourceStatus status, ErrorCode errorCode,
                                        String message, Throwable cause, Duration elapsed) {
        return new SourceOutcome<>(sourceId, status, null, elapsed, errorCode, message, cause);
    }

    boolean isSuccess() {
        return status == SourceStatus.SUCCESS;
    }

    SourceTrace toTrace() {
        return new SourceTrace(sourceId, status, elapsed, errorCode, message);
    }
}
