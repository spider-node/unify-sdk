package com.unify.sdk.api;

import java.util.Objects;

/**
 * 对外暴露的一项能力，同时是调用方使用的类型安全"键"。
 * <p>
 * 一项能力由名称、统一请求类型 {@code Q}、统一响应类型 {@code R} 三者确定。
 * 调用方只面向能力编程，不感知内部由哪些接口、哪些数据结构提供支撑。
 *
 * <pre>{@code
 * public static final Capability<CustomerQuery, CustomerProfile> QUERY_CUSTOMER =
 *         Capability.of("customer.query", CustomerQuery.class, CustomerProfile.class);
 * }</pre>
 *
 * @param <Q> 统一请求类型
 * @param <R> 统一响应类型
 */
public final class Capability<Q, R> {

    private final String name;
    private final Class<Q> requestType;
    private final Class<R> responseType;

    private Capability(String name, Class<Q> requestType, Class<R> responseType) {
        this.name = name;
        this.requestType = requestType;
        this.responseType = responseType;
    }

    public static <Q, R> Capability<Q, R> of(String name, Class<Q> requestType, Class<R> responseType) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("capability name must not be blank");
        }
        return new Capability<>(name,
                Objects.requireNonNull(requestType, "requestType"),
                Objects.requireNonNull(responseType, "responseType"));
    }

    public String name() {
        return name;
    }

    public Class<Q> requestType() {
        return requestType;
    }

    public Class<R> responseType() {
        return responseType;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Capability<?, ?> other)) {
            return false;
        }
        return name.equals(other.name)
                && requestType.equals(other.requestType)
                && responseType.equals(other.responseType);
    }

    @Override
    public int hashCode() {
        return name.hashCode();
    }

    @Override
    public String toString() {
        return "Capability[" + name + ": " + requestType.getSimpleName() + " -> " + responseType.getSimpleName() + "]";
    }
}
