package com.unify.sdk.api;

import java.util.List;

/**
 * 统一响应及调用元数据。
 *
 * @param data       统一响应数据，内部接口未查到数据时可能为 {@code null}
 * @param traceId    本次调用的追踪 ID
 * @param capability 能力名称
 * @param traces     各内部数据源的调用轨迹，按调用顺序排列
 * @param degraded   是否降级：存在数据源失败但仍返回了结果
 */
public record UnifiedResult<R>(R data, String traceId, String capability, List<SourceTrace> traces, boolean degraded) {

    public UnifiedResult {
        traces = traces == null ? List.of() : List.copyOf(traces);
    }

    /** 实际成功提供数据的数据源 ID。 */
    public List<String> successfulSources() {
        return traces.stream()
                .filter(t -> t.status() == SourceStatus.SUCCESS)
                .map(SourceTrace::sourceId)
                .toList();
    }
}
