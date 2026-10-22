package com.unify.sdk.spi;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

/**
 * 传递给数据源的调用上下文。
 */
public final class SourceContext {

    private final String capability;
    private final String traceId;
    private final Map<String, Object> attributes;
    private final Instant deadline;

    public SourceContext(String capability, String traceId, Map<String, Object> attributes, Instant deadline) {
        this.capability = capability;
        this.traceId = traceId;
        this.attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
        this.deadline = deadline;
    }

    /** 触发本次调用的能力名称。 */
    public String capability() {
        return capability;
    }

    public String traceId() {
        return traceId;
    }

    /** 调用方透传的属性，例如租户、操作人。 */
    public Map<String, Object> attributes() {
        return attributes;
    }

    public Optional<Object> attribute(String key) {
        return Optional.ofNullable(attributes.get(key));
    }

    /** 本次调用的截止时间，超过后 SDK 将放弃等待该数据源。 */
    public Optional<Instant> deadline() {
        return Optional.ofNullable(deadline);
    }

    /** 距离截止时间的剩余时长，已超时返回 {@link Duration#ZERO}。 */
    public Optional<Duration> remaining() {
        if (deadline == null) {
            return Optional.empty();
        }
        Duration remaining = Duration.between(Instant.now(), deadline);
        return Optional.of(remaining.isNegative() ? Duration.ZERO : remaining);
    }
}
