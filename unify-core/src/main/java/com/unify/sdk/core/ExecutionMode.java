package com.unify.sdk.core;

/**
 * 一项能力的多个候选数据源的执行方式。
 */
public enum ExecutionMode {

    /**
     * 顺序回退：按顺序逐个尝试，第一个成功的结果即为最终结果；失败或超时则换下一个。
     * 适用于"同一份数据在不同系统中，按规则只需取一处"的场景，例如新老系统迁移。
     */
    FIRST_AVAILABLE,

    /**
     * 并行合并：同时调用所有候选数据源，再用 {@link ResultMerger} 合并为一个统一结果。
     * 适用于"统一结果由多个系统的数据拼装而成"的场景。
     */
    FAN_OUT_MERGE
}
