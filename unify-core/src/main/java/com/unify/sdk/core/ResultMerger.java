package com.unify.sdk.core;

import java.util.List;

/**
 * 合并器：把多个数据源各自转换出的统一结果合并为一个，用于 {@link ExecutionMode#FAN_OUT_MERGE}。
 * <p>
 * 传入的结果顺序与路由顺序一致，且只包含调用成功的数据源。常用实现见 {@link Mergers}。
 */
@FunctionalInterface
public interface ResultMerger<R> {

    R merge(List<SourcedResult<R>> results, InvocationContext context);
}
