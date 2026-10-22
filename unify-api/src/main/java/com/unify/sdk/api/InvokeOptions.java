package com.unify.sdk.api;

import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * 单次调用的可选参数。不设置的项使用能力级或全局默认值。
 */
public final class InvokeOptions {

    private static final InvokeOptions DEFAULTS = builder().build();

    private final Duration timeout;
    private final FailurePolicy failurePolicy;
    private final String preferredSource;
    private final String traceId;
    private final Map<String, Object> attributes;

    private InvokeOptions(Builder builder) {
        this.timeout = builder.timeout;
        this.failurePolicy = builder.failurePolicy;
        this.preferredSource = builder.preferredSource;
        this.traceId = builder.traceId;
        this.attributes = Collections.unmodifiableMap(new LinkedHashMap<>(builder.attributes));
    }

    public static InvokeOptions defaults() {
        return DEFAULTS;
    }

    public static Builder builder() {
        return new Builder();
    }

    /** 整次调用的超时时间（包含所有内部接口调用及转换）。 */
    public Optional<Duration> timeout() {
        return Optional.ofNullable(timeout);
    }

    public Optional<FailurePolicy> failurePolicy() {
        return Optional.ofNullable(failurePolicy);
    }

    /**
     * 优先使用的内部数据源 ID，仅作为提示：若该数据源可处理本次请求，则排在第一位尝试。
     * 正常业务不应依赖此项，主要用于灰度验证和问题排查。
     */
    public Optional<String> preferredSource() {
        return Optional.ofNullable(preferredSource);
    }

    public Optional<String> traceId() {
        return Optional.ofNullable(traceId);
    }

    /** 透传给适配器和数据源的附加信息，例如租户、操作人。 */
    public Map<String, Object> attributes() {
        return attributes;
    }

    public static final class Builder {

        private Duration timeout;
        private FailurePolicy failurePolicy;
        private String preferredSource;
        private String traceId;
        private final Map<String, Object> attributes = new LinkedHashMap<>();

        private Builder() {
        }

        public Builder timeout(Duration timeout) {
            if (timeout != null && (timeout.isNegative() || timeout.isZero())) {
                throw new IllegalArgumentException("timeout must be positive");
            }
            this.timeout = timeout;
            return this;
        }

        public Builder failurePolicy(FailurePolicy failurePolicy) {
            this.failurePolicy = failurePolicy;
            return this;
        }

        public Builder preferredSource(String preferredSource) {
            this.preferredSource = preferredSource;
            return this;
        }

        public Builder traceId(String traceId) {
            this.traceId = traceId;
            return this;
        }

        public Builder attribute(String key, Object value) {
            attributes.put(Objects.requireNonNull(key, "key"), Objects.requireNonNull(value, "value"));
            return this;
        }

        public Builder attributes(Map<String, ?> values) {
            if (values != null) {
                values.forEach(this::attribute);
            }
            return this;
        }

        public InvokeOptions build() {
            return new InvokeOptions(this);
        }
    }
}
