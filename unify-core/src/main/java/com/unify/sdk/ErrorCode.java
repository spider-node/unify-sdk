package com.unify.sdk;

public enum ErrorCode {

    /** 源数据不是合法的 JSON。 */
    INVALID_SOURCE,

    /** DSL 不是合法的 JSON，或结构、路径、表达式有误，或函数参数个数不符。 */
    INVALID_DSL,

    /** DSL 引用了未注册的函数。 */
    UNKNOWN_FUNCTION,

    /** 按名称转换时，映射未注册。 */
    MAPPING_NOT_FOUND,

    /** 函数执行失败，通常是源数据内容不符合预期，例如日期格式不对、数字无法解析。 */
    FUNCTION_FAILED,

    /** 输出序列化失败或其他内部错误。 */
    INTERNAL_ERROR
}
