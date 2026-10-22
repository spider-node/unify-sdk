package com.unify.sdk.core;

import com.unify.sdk.api.InvokeOptions;
import com.unify.sdk.spi.SourceContext;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * 一次能力调用的上下文，在适配器、路由器、合并器、拦截器之间传递。
 */
public final class InvocationContext {

    private final String capability;
    private final String traceId;
    private final InvokeOptions options;
    private final Instant deadline;

    private InvocationContext(String capability, String traceId, InvokeOptions options, Instant deadline) {
        this.capability = Objects.requireNonNull(capability, "capability");
        this.traceId = Objects.requireNonNull(traceId, "traceId");
        this.options = options == null ? InvokeOptions.defaults() : options;
        this.deadline = deadline;
    }

    /**
     * 创建上下文。SDK 内部使用，也可在单元测试中直接构造。
     *
     * @param deadline 截止时间，可为 {@code null}
     */
    public static InvocationContext create(String capability, String traceId, InvokeOptions options, Instant deadline) {
        return new InvocationContext(capability, traceId, options, deadline);
    }

    public String capability() {
        return capability;
    }

    public String traceId() {
        return traceId;
    }

    public InvokeOptions options() {
        return options;
    }

    public Optional<Instant> deadline() {
        return Optional.ofNullable(deadline);
    }

    public Map<String, Object> attributes() {
        return options.attributes();
    }

    public Optional<Object> attribute(String key) {
        return Optional.ofNullable(options.attributes().get(key));
    }

    public <T> Optional<T> attribute(String key, Class<T> type) {
        Object value = options.attributes().get(key);
        return type.isInstance(value) ? Optional.of(type.cast(value)) : Optional.empty();
    }

    /** 转换为传递给数据源的上下文。 */
    public SourceContext toSourceContext() {
        return new SourceContext(capability, traceId, options.attributes(), deadline);
    }
}
