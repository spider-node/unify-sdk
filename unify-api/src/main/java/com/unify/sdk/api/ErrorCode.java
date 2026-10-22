package com.unify.sdk.api;

public enum ErrorCode {

    /** 能力未注册。 */
    CAPABILITY_NOT_FOUND,

    /** 请求为空、类型不匹配，或能力定义与注册的不一致。 */
    INVALID_REQUEST,

    /** 没有可处理本次请求的数据源，或所有数据源均失败。 */
    NO_AVAILABLE_SOURCE,

    /** 数据源调用失败。 */
    SOURCE_FAILED,

    /** 数据源调用超时。 */
    SOURCE_TIMEOUT,

    /** 统一结构与内部结构之间转换失败。 */
    CONVERT_FAILED,

    /** 多数据源结果合并失败。 */
    MERGE_FAILED,

    /** SDK 内部错误。 */
    INTERNAL_ERROR
}
