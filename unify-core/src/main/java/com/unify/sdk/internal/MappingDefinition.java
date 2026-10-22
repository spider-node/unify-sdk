package com.unify.sdk.internal;

/**
 * 编译后的映射。
 *
 * @param name        映射名称，匿名映射为 {@code null}
 * @param description 描述，可为 {@code null}
 * @param rule        根规则
 */
public record MappingDefinition(String name, String description, Rule rule) {
}
