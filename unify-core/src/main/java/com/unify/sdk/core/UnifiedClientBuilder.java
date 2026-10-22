package com.unify.sdk.core;

import com.unify.sdk.api.Capability;
import com.unify.sdk.api.FailurePolicy;
import com.unify.sdk.api.UnifiedClient;
import com.unify.sdk.spi.DataSource;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutorService;

/**
 * 统一客户端构建器，通过 {@link UnifiedClients#builder()} 获取。
 * <p>
 * {@link #build()} 时会做完整校验，配置错误在启动期即暴露：
 * 适配器找不到数据源、数据源 ID 重复、同一能力定义冲突、并行合并缺少合并器等。
 */
public final class UnifiedClientBuilder {

    private final List<DataSource<?, ?>> dataSources = new ArrayList<>();
    private final List<SourceAdapter<?, ?, ?, ?>> adapters = new ArrayList<>();
    private final Map<String, CapabilityConfig<?, ?>> configs = new LinkedHashMap<>();
    private final List<InvokeInterceptor> interceptors = new ArrayList<>();
    private Duration defaultTimeout = Duration.ofSeconds(3);
    private FailurePolicy defaultFailurePolicy = FailurePolicy.BEST_EFFORT;
    private ExecutorService executor;

    UnifiedClientBuilder() {
    }

    public UnifiedClientBuilder dataSource(DataSource<?, ?> dataSource) {
        dataSources.add(Objects.requireNonNull(dataSource, "dataSource"));
        return this;
    }

    public UnifiedClientBuilder dataSources(Collection<? extends DataSource<?, ?>> dataSources) {
        dataSources.forEach(this::dataSource);
        return this;
    }

    public UnifiedClientBuilder adapter(SourceAdapter<?, ?, ?, ?> adapter) {
        adapters.add(Objects.requireNonNull(adapter, "adapter"));
        return this;
    }

    public UnifiedClientBuilder adapters(Collection<? extends SourceAdapter<?, ?, ?, ?>> adapters) {
        adapters.forEach(this::adapter);
        return this;
    }

    /**
     * 类型安全地同时注册数据源与适配器，编译期即可保证两者的内部请求、响应结构一致。
     */
    public <Q, R, SQ, SR> UnifiedClientBuilder bind(DataSource<SQ, SR> dataSource, SourceAdapter<Q, R, SQ, SR> adapter) {
        Objects.requireNonNull(dataSource, "dataSource");
        Objects.requireNonNull(adapter, "adapter");
        if (!Objects.equals(dataSource.sourceId(), adapter.sourceId())) {
            throw new IllegalArgumentException("sourceId mismatch: dataSource '" + dataSource.sourceId()
                    + "' vs adapter '" + adapter.sourceId() + "'");
        }
        return dataSource(dataSource).adapter(adapter);
    }

    /** 为某项能力指定执行配置；未配置的能力使用默认配置（顺序回退）。 */
    public UnifiedClientBuilder capability(CapabilityConfig<?, ?> config) {
        Objects.requireNonNull(config, "config");
        String name = config.capability().name();
        if (configs.putIfAbsent(name, config) != null) {
            throw new IllegalArgumentException("Duplicate config for capability '" + name + "'");
        }
        return this;
    }

    public UnifiedClientBuilder interceptor(InvokeInterceptor interceptor) {
        interceptors.add(Objects.requireNonNull(interceptor, "interceptor"));
        return this;
    }

    /** 全局默认超时，默认 3 秒。 */
    public UnifiedClientBuilder defaultTimeout(Duration timeout) {
        Objects.requireNonNull(timeout, "timeout");
        if (timeout.isNegative() || timeout.isZero()) {
            throw new IllegalArgumentException("timeout must be positive");
        }
        this.defaultTimeout = timeout;
        return this;
    }

    /** 全局默认失败策略，默认 {@link FailurePolicy#BEST_EFFORT}。 */
    public UnifiedClientBuilder defaultFailurePolicy(FailurePolicy failurePolicy) {
        this.defaultFailurePolicy = Objects.requireNonNull(failurePolicy, "failurePolicy");
        return this;
    }

    /**
     * 使用外部线程池调用数据源。外部线程池的生命周期由调用方管理，{@link UnifiedClient#close()} 不会关闭它。
     * 不设置时，Java 21+ 使用虚拟线程，低版本使用内置的有界线程池。
     */
    public UnifiedClientBuilder executor(ExecutorService executor) {
        this.executor = Objects.requireNonNull(executor, "executor");
        return this;
    }

    public UnifiedClient build() {
        Map<String, DataSource<?, ?>> sourceIndex = indexDataSources();
        Map<String, Capability<?, ?>> capabilityIndex = new LinkedHashMap<>();
        Map<String, List<SourceAdapter<?, ?, ?, ?>>> adaptersByCapability = new LinkedHashMap<>();

        for (SourceAdapter<?, ?, ?, ?> adapter : adapters) {
            String adapterName = adapter.getClass().getName();
            String sourceId = adapter.sourceId();
            Capability<?, ?> capability = adapter.capability();
            if (sourceId == null || sourceId.isBlank()) {
                throw new IllegalStateException("Adapter " + adapterName + " has a blank sourceId");
            }
            if (capability == null) {
                throw new IllegalStateException("Adapter " + adapterName + " has no capability");
            }
            if (!sourceIndex.containsKey(sourceId)) {
                throw new IllegalStateException("No DataSource registered for sourceId '" + sourceId
                        + "' required by adapter " + adapterName);
            }
            Capability<?, ?> existing = capabilityIndex.putIfAbsent(capability.name(), capability);
            if (existing != null && !existing.equals(capability)) {
                throw new IllegalStateException("Conflicting definitions for capability '" + capability.name()
                        + "': " + existing + " vs " + capability);
            }
            List<SourceAdapter<?, ?, ?, ?>> group =
                    adaptersByCapability.computeIfAbsent(capability.name(), key -> new ArrayList<>());
            for (SourceAdapter<?, ?, ?, ?> registered : group) {
                if (registered.sourceId().equals(sourceId)) {
                    throw new IllegalStateException("Duplicate adapter for capability '" + capability.name()
                            + "' and source '" + sourceId + "'");
                }
            }
            group.add(adapter);
        }

        for (Map.Entry<String, CapabilityConfig<?, ?>> entry : configs.entrySet()) {
            Capability<?, ?> registered = capabilityIndex.get(entry.getKey());
            if (registered == null) {
                throw new IllegalStateException("Capability '" + entry.getKey() + "' is configured but has no adapter");
            }
            if (!registered.equals(entry.getValue().capability())) {
                throw new IllegalStateException("Conflicting definitions for capability '" + entry.getKey()
                        + "': " + registered + " vs " + entry.getValue().capability());
            }
        }

        Map<String, CapabilityRuntime<?, ?>> runtimes = new LinkedHashMap<>();
        for (Map.Entry<String, List<SourceAdapter<?, ?, ?, ?>>> entry : adaptersByCapability.entrySet()) {
            String name = entry.getKey();
            runtimes.put(name, createRuntime(capabilityIndex.get(name), entry.getValue(), configs.get(name), sourceIndex));
        }

        boolean ownsExecutor = executor == null;
        ExecutorService effectiveExecutor = ownsExecutor ? ExecutorFactory.createDefault() : executor;
        return new DefaultUnifiedClient(runtimes, interceptors, defaultTimeout, defaultFailurePolicy,
                effectiveExecutor, ownsExecutor);
    }

    private Map<String, DataSource<?, ?>> indexDataSources() {
        Map<String, DataSource<?, ?>> index = new LinkedHashMap<>();
        for (DataSource<?, ?> dataSource : dataSources) {
            String sourceId = dataSource.sourceId();
            if (sourceId == null || sourceId.isBlank()) {
                throw new IllegalStateException("DataSource " + dataSource.getClass().getName() + " has a blank sourceId");
            }
            DataSource<?, ?> existing = index.putIfAbsent(sourceId, dataSource);
            if (existing != null && existing != dataSource) {
                throw new IllegalStateException("Duplicate DataSource id '" + sourceId + "': "
                        + existing.getClass().getName() + " and " + dataSource.getClass().getName());
            }
        }
        return index;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static <Q, R> CapabilityRuntime<Q, R> createRuntime(Capability<Q, R> capability,
                                                                List<SourceAdapter<?, ?, ?, ?>> adapters,
                                                                CapabilityConfig<?, ?> config,
                                                                Map<String, DataSource<?, ?>> sourceIndex) {
        CapabilityConfig<Q, R> typedConfig = config == null
                ? CapabilityConfig.defaults(capability)
                : (CapabilityConfig<Q, R>) config;
        List<SourceBinding<Q, R>> bindings = new ArrayList<>();
        for (SourceAdapter adapter : adapters) {
            DataSource dataSource = sourceIndex.get(adapter.sourceId());
            bindings.add(new SourceBinding<Q, R>((SourceAdapter<Q, R, Object, Object>) adapter,
                    (DataSource<Object, Object>) dataSource));
        }
        bindings.sort(Comparator.comparingInt(binding -> binding.order()));
        return new CapabilityRuntime<>(capability, typedConfig, bindings);
    }
}
