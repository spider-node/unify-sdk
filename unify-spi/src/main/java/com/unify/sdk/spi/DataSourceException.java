package com.unify.sdk.spi;

/**
 * 数据源调用失败时抛出的异常。
 */
public class DataSourceException extends RuntimeException {

    private final String errorCode;
    private final boolean retryable;

    public DataSourceException(String message) {
        this(message, null, false, null);
    }

    public DataSourceException(String message, Throwable cause) {
        this(message, null, false, cause);
    }

    /**
     * @param message   错误描述
     * @param errorCode 内部接口自身的错误码，可为 {@code null}
     * @param retryable 是否可重试（例如网络抖动、限流）
     * @param cause     原始异常
     */
    public DataSourceException(String message, String errorCode, boolean retryable, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.retryable = retryable;
    }

    public String errorCode() {
        return errorCode;
    }

    public boolean retryable() {
        return retryable;
    }
}
