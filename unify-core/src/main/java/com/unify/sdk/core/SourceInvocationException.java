package com.unify.sdk.core;

import com.unify.sdk.api.ErrorCode;

/**
 * 单个数据源链路内部失败时使用，携带错误码，由引擎转换为调用轨迹。
 */
final class SourceInvocationException extends RuntimeException {

    private final ErrorCode code;

    SourceInvocationException(ErrorCode code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    ErrorCode code() {
        return code;
    }
}
