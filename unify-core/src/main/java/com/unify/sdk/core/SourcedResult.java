package com.unify.sdk.core;

/**
 * 带来源标识的统一结果。
 *
 * @param sourceId 提供该结果的数据源 ID
 * @param data     已转换好的统一结果，可能为 {@code null}
 */
public record SourcedResult<R>(String sourceId, R data) {
}
