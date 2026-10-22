package com.unify.sdk.api;

/**
 * 多数据源并行调用（FAN_OUT_MERGE 模式）时，部分数据源失败的处理方式。
 * <p>
 * 顺序回退模式（FIRST_AVAILABLE）本身就是"失败后换下一个"，不受此策略影响。
 */
public enum FailurePolicy {

    /** 任一数据源失败即整体失败。适用于各数据源数据缺一不可的场景。 */
    FAIL_FAST,

    /** 只要有数据源成功即返回已有数据，并在结果中标记为降级。 */
    BEST_EFFORT
}
