package com.unify.sdk.internal;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * 编译后的 DSL 节点。实现必须不可变、线程安全。
 * <p>
 * 返回值可能为 {@code null}、NullNode 或 MissingNode，调用方需通过 {@link Nodes#isNull(JsonNode)} 判断。
 */
@FunctionalInterface
public interface Rule {

    JsonNode evaluate(EvalContext context);
}
