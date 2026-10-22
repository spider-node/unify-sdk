package com.unify.sdk.spring;

import com.unify.sdk.api.UnifiedClient;
import com.unify.sdk.core.CapabilityConfig;
import com.unify.sdk.core.InvokeInterceptor;
import com.unify.sdk.core.SourceAdapter;
import com.unify.sdk.core.UnifiedClientBuilder;
import com.unify.sdk.core.UnifiedClients;
import com.unify.sdk.core.interceptor.LoggingInterceptor;
import com.unify.sdk.spi.DataSource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * 自动收集容器中所有的 {@link DataSource}、{@link SourceAdapter}、{@link CapabilityConfig}、
 * {@link InvokeInterceptor} Bean，构建并注册唯一的 {@link UnifiedClient}。
 * <p>
 * 业务方只需注入 {@link UnifiedClient}；数据提供方只需把数据源和适配器声明为 Bean。
 * 配置错误（例如适配器找不到数据源）会在应用启动时直接失败。
 */
@AutoConfiguration
@ConditionalOnClass(UnifiedClient.class)
@ConditionalOnProperty(prefix = "unify", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(UnifyProperties.class)
public class UnifyAutoConfiguration {

    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean
    public UnifiedClient unifiedClient(UnifyProperties properties,
                                       ObjectProvider<DataSource<?, ?>> dataSources,
                                       ObjectProvider<SourceAdapter<?, ?, ?, ?>> adapters,
                                       ObjectProvider<CapabilityConfig<?, ?>> capabilityConfigs,
                                       ObjectProvider<InvokeInterceptor> interceptors,
                                       ObjectProvider<UnifiedClientCustomizer> customizers) {
        UnifiedClientBuilder builder = UnifiedClients.builder()
                .defaultTimeout(properties.getDefaultTimeout())
                .defaultFailurePolicy(properties.getDefaultFailurePolicy());
        dataSources.orderedStream().forEach(builder::dataSource);
        adapters.orderedStream().forEach(builder::adapter);
        capabilityConfigs.orderedStream().forEach(builder::capability);
        if (properties.isLoggingEnabled()) {
            builder.interceptor(new LoggingInterceptor());
        }
        interceptors.orderedStream().forEach(builder::interceptor);
        customizers.orderedStream().forEach(customizer -> customizer.customize(builder));
        return builder.build();
    }
}
