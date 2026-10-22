package com.unify.sdk;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.NullNode;
import com.unify.sdk.internal.EvalContext;
import com.unify.sdk.internal.MappingDefinition;

/**
 * 编译后的映射，不可变、线程安全，可以反复使用。由 {@link JsonTransformer#compile(String)} 或
 * {@link JsonTransformer#mapping(String)} 得到。
 */
public final class CompiledMapping {

    private final MappingDefinition definition;
    private final ObjectMapper objectMapper;

    CompiledMapping(MappingDefinition definition, ObjectMapper objectMapper) {
        this.definition = definition;
        this.objectMapper = objectMapper;
    }

    /** 映射名称，DSL 未声明 name 时为 {@code null}。 */
    public String name() {
        return definition.name();
    }

    /** 映射描述，DSL 未声明 description 时为 {@code null}。 */
    public String description() {
        return definition.description();
    }

    /**
     * 转换源数据。返回的节点与源数据互不共享，调用方可以随意修改。
     *
     * @param source 源数据，{@code null} 视为 JSON null
     */
    public JsonNode apply(JsonNode source) {
        return evaluate(source).deepCopy();
    }

    /**
     * 转换 JSON 文本形式的源数据，返回 JSON 文本。
     *
     * @param sourceJson 源数据 JSON，{@code null} 视为 JSON null
     * @throws TransformException 源数据不是合法 JSON 时错误码为 {@link ErrorCode#INVALID_SOURCE}
     */
    public String apply(String sourceJson) {
        JsonNode result = evaluate(readSource(sourceJson));
        try {
            return objectMapper.writeValueAsString(result);
        } catch (JsonProcessingException e) {
            throw new TransformException(ErrorCode.INTERNAL_ERROR, name(), null,
                    "输出 JSON 序列化失败：" + e.getOriginalMessage(), e);
        }
    }

    private JsonNode evaluate(JsonNode source) {
        try {
            return definition.rule().evaluate(EvalContext.of(name(), source));
        } catch (TransformException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new TransformException(ErrorCode.INTERNAL_ERROR, name(), null,
                    "转换时发生内部错误：" + e, e);
        }
    }

    private JsonNode readSource(String sourceJson) {
        if (sourceJson == null) {
            return NullNode.getInstance();
        }
        JsonNode source;
        try {
            source = objectMapper.readTree(sourceJson);
        } catch (JsonProcessingException e) {
            throw new TransformException(ErrorCode.INVALID_SOURCE, name(), null,
                    "源数据不是合法的 JSON：" + e.getOriginalMessage(), e);
        }
        if (source == null || source.isMissingNode()) {
            throw new TransformException(ErrorCode.INVALID_SOURCE, name(), null, "源数据为空", null);
        }
        return source;
    }

    @Override
    public String toString() {
        return "CompiledMapping[" + (name() == null ? "<anonymous>" : name()) + "]";
    }
}
