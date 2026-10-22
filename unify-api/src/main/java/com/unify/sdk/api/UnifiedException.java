package com.unify.sdk.api;

import java.util.List;

/**
 * SDK 对调用方抛出的唯一异常类型。内部数据源的各类异常都会被包装为此异常，
 * 调用方无需处理任何内部接口特有的异常。
 */
public class UnifiedException extends RuntimeException {

    private final ErrorCode code;
    private final String capability;
    private final List<SourceTrace> traces;

    public UnifiedException(ErrorCode code, String message) {
        this(code, message, null, List.of(), null);
    }

    public UnifiedException(ErrorCode code, String message, Throwable cause) {
        this(code, message, null, List.of(), cause);
    }

    public UnifiedException(ErrorCode code, String message, String capability, List<SourceTrace> traces, Throwable cause) {
        super("[" + code + "] " + message, cause);
        this.code = code;
        this.capability = capability;
        this.traces = traces == null ? List.of() : List.copyOf(traces);
    }

    public ErrorCode code() {
        return code;
    }

    /** 出错的能力名称，可能为 {@code null}。 */
    public String capability() {
        return capability;
    }

    /** 出错前各数据源的调用轨迹。 */
    public List<SourceTrace> traces() {
        return traces;
    }
}
