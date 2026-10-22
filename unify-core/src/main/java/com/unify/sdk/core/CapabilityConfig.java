package com.unify.sdk.core;

import com.unify.sdk.api.Capability;
import com.unify.sdk.api.FailurePolicy;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

/**
 * 一项能力的执行配置。未单独配置的能力使用 {@link #defaults(Capability)}：
 * 顺序回退、使用全部候选数据源、超时与失败策略取全局默认值。
 */
public final class CapabilityConfig<Q, R> {

    private final Capability<Q, R> capability;
    private final ExecutionMode mode;
    private final Router<Q> router;
    private final ResultMerger<R> merger;
    private final Duration timeout;
    private final FailurePolicy failurePolicy;

    private CapabilityConfig(Builder<Q, R> builder) {
        this.capability = builder.capability;
        this.mode = builder.mode;
        this.router = builder.router;
        this.merger = builder.merger;
        this.timeout = builder.timeout;
        this.failurePolicy = builder.failurePolicy;
    }

    public static <Q, R> Builder<Q, R> builder(Capability<Q, R> capability) {
        return new Builder<>(capability);
    }

    public static <Q, R> CapabilityConfig<Q, R> defaults(Capability<Q, R> capability) {
        return builder(capability).build();
    }

    public Capability<Q, R> capability() {
        return capability;
    }

    public ExecutionMode mode() {
        return mode;
    }

    public Router<Q> router() {
        return router;
    }

    /** 仅 {@link ExecutionMode#FAN_OUT_MERGE} 模式使用。 */
    public Optional<ResultMerger<R>> merger() {
        return Optional.ofNullable(merger);
    }

    public Optional<Duration> timeout() {
        return Optional.ofNullable(timeout);
    }

    public Optional<FailurePolicy> failurePolicy() {
        return Optional.ofNullable(failurePolicy);
    }

    public static final class Builder<Q, R> {

        private final Capability<Q, R> capability;
        private ExecutionMode mode = ExecutionMode.FIRST_AVAILABLE;
        private Router<Q> router = Router.all();
        private ResultMerger<R> merger;
        private Duration timeout;
        private FailurePolicy failurePolicy;

        private Builder(Capability<Q, R> capability) {
            this.capability = Objects.requireNonNull(capability, "capability");
        }

        public Builder<Q, R> mode(ExecutionMode mode) {
            this.mode = Objects.requireNonNull(mode, "mode");
            return this;
        }

        public Builder<Q, R> router(Router<Q> router) {
            this.router = Objects.requireNonNull(router, "router");
            return this;
        }

        /** 设置合并器，并自动切换为 {@link ExecutionMode#FAN_OUT_MERGE} 模式。 */
        public Builder<Q, R> fanOutMerge(ResultMerger<R> merger) {
            this.merger = Objects.requireNonNull(merger, "merger");
            this.mode = ExecutionMode.FAN_OUT_MERGE;
            return this;
        }

        public Builder<Q, R> timeout(Duration timeout) {
            if (timeout != null && (timeout.isNegative() || timeout.isZero())) {
                throw new IllegalArgumentException("timeout must be positive");
            }
            this.timeout = timeout;
            return this;
        }

        public Builder<Q, R> failurePolicy(FailurePolicy failurePolicy) {
            this.failurePolicy = failurePolicy;
            return this;
        }

        public CapabilityConfig<Q, R> build() {
            if (mode == ExecutionMode.FAN_OUT_MERGE && merger == null) {
                throw new IllegalStateException("FAN_OUT_MERGE mode requires a ResultMerger: " + capability.name());
            }
            return new CapabilityConfig<>(this);
        }
    }
}
