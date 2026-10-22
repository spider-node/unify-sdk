package com.unify.sdk.spring;

import com.unify.sdk.JsonTransformer;
import com.unify.sdk.function.FunctionProvider;
import com.unify.sdk.function.TransformFunction;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * 注册 {@link JsonTransformer}：
 * <ol>
 *     <li>{@code unify.discover-functions=true} 时加载 ServiceLoader 声明的 {@link FunctionProvider}；</li>
 *     <li>注册容器中的 {@link FunctionProvider} Bean；</li>
 *     <li>注册容器中的 {@link TransformFunction} Bean，<b>函数名即 Bean 名</b>，
 *     例如 {@code @Component("maskPhone")} 或 {@code @Bean TransformFunction maskPhone()}；</li>
 *     <li>预注册 {@code unify.mapping-locations} 下的映射文件；</li>
 *     <li>依次执行 {@link TransformerCustomizer}。</li>
 * </ol>
 * 映射文件或函数有误时启动失败。容器中已有 {@link JsonTransformer} 时不再注册。
 */
@AutoConfiguration
@ConditionalOnClass(JsonTransformer.class)
@ConditionalOnProperty(prefix = "unify", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(UnifyProperties.class)
public class UnifyAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public JsonTransformer jsonTransformer(UnifyProperties properties, ApplicationContext context,
            ObjectProvider<FunctionProvider> functionProviders, ObjectProvider<TransformerCustomizer> customizers) {
        JsonTransformer.Builder builder = JsonTransformer.builder().cacheSize(properties.getCacheSize());
        if (properties.isDiscoverFunctions()) {
            builder.discoverFunctions();
        }
        functionProviders.orderedStream().forEach(builder::functions);
        registerFunctionBeans(builder, context);
        registerMappings(builder, context, properties);
        customizers.orderedStream().forEach(customizer -> customizer.customize(builder));
        return builder.build();
    }

    private static void registerFunctionBeans(JsonTransformer.Builder builder, ApplicationContext context) {
        Map<String, TransformFunction> beans = context.getBeansOfType(TransformFunction.class);
        for (Map.Entry<String, TransformFunction> entry : beans.entrySet()) {
            try {
                builder.function(entry.getKey(), entry.getValue());
            } catch (IllegalArgumentException | IllegalStateException e) {
                throw new IllegalStateException("无法注册函数 Bean " + entry.getKey() + "：" + e.getMessage(), e);
            }
        }
    }

    private static void registerMappings(JsonTransformer.Builder builder, ApplicationContext context,
            UnifyProperties properties) {
        Set<Resource> resources = new LinkedHashSet<>();
        for (String location : properties.getMappingLocations()) {
            if (location == null || location.isBlank()) {
                continue;
            }
            try {
                Resource[] found = context.getResources(location.strip());
                Arrays.sort(found, Comparator.comparing(Resource::getDescription));
                resources.addAll(Arrays.asList(found));
            } catch (IOException e) {
                throw new UncheckedIOException("无法解析映射位置：" + location, e);
            }
        }
        for (Resource resource : resources) {
            if (!resource.isReadable()) {
                continue;
            }
            try {
                builder.mapping(resource.getContentAsString(StandardCharsets.UTF_8), resource.getDescription());
            } catch (IOException e) {
                throw new UncheckedIOException("无法读取映射文件：" + resource.getDescription(), e);
            }
        }
    }
}
