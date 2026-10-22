package com.unify.sdk.core.convert;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 码值映射表：把各内部系统的码值（状态码、类型码等）映射为统一枚举或统一码值。
 *
 * <pre>{@code
 * static final CodeMapper<String, CustomerStatus> STATUS = CodeMapper.<String, CustomerStatus>builder("legacy.status")
 *         .map("1", CustomerStatus.ACTIVE)
 *         .map("2", CustomerStatus.FROZEN)
 *         .defaultValue(CustomerStatus.UNKNOWN)
 *         .build();
 * }</pre>
 *
 * @param <S> 内部码值类型
 * @param <T> 统一码值类型
 */
public final class CodeMapper<S, T> {

    private final String name;
    private final Map<S, T> mapping;
    private final T defaultValue;
    private final boolean strict;

    private CodeMapper(Builder<S, T> builder) {
        this.name = builder.name;
        this.mapping = Collections.unmodifiableMap(new LinkedHashMap<>(builder.mapping));
        this.defaultValue = builder.defaultValue;
        this.strict = builder.strict;
    }

    public static <S, T> Builder<S, T> builder(String name) {
        return new Builder<>(name);
    }

    /**
     * 映射码值。输入为 {@code null} 时返回默认值；未登记的码值在严格模式下抛出异常，否则返回默认值。
     */
    public T map(S source) {
        if (source == null) {
            return defaultValue;
        }
        T target = mapping.get(source);
        if (target != null) {
            return target;
        }
        if (strict) {
            throw new IllegalArgumentException("Unmapped code '" + source + "' in dictionary '" + name + "'");
        }
        return defaultValue;
    }

    public String name() {
        return name;
    }

    public Map<S, T> mapping() {
        return mapping;
    }

    public static final class Builder<S, T> {

        private final String name;
        private final Map<S, T> mapping = new LinkedHashMap<>();
        private T defaultValue;
        private boolean strict;

        private Builder(String name) {
            this.name = Objects.requireNonNull(name, "name");
        }

        public Builder<S, T> map(S source, T target) {
            mapping.put(Objects.requireNonNull(source, "source"), Objects.requireNonNull(target, "target"));
            return this;
        }

        public Builder<S, T> defaultValue(T defaultValue) {
            this.defaultValue = defaultValue;
            return this;
        }

        /** 严格模式：遇到未登记的码值时抛出异常（最终表现为 CONVERT_FAILED），而不是返回默认值。 */
        public Builder<S, T> strict() {
            this.strict = true;
            return this;
        }

        public CodeMapper<S, T> build() {
            return new CodeMapper<>(this);
        }
    }
}
