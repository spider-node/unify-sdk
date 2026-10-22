package com.unify.sdk.internal;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * 一次转换的求值上下文。
 *
 * @param mappingName 映射名称，用于错误信息
 * @param root        源数据根节点，对应路径 {@code $$}
 * @param current     当前数据，对应路径 {@code $}；顶层等于 root，{@code $each} 内为当前元素
 */
public record EvalContext(String mappingName, JsonNode root, JsonNode current) {

    public static EvalContext of(String mappingName, JsonNode source) {
        JsonNode root = Nodes.orNull(source);
        return new EvalContext(mappingName, root, root);
    }

    public EvalContext withCurrent(JsonNode node) {
        return new EvalContext(mappingName, root, Nodes.orNull(node));
    }
}
