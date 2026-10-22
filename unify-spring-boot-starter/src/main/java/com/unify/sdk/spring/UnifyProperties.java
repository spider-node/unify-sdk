package com.unify.sdk.spring;

import com.unify.sdk.JsonTransformer;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * {@code unify.*} 配置项。
 */
@ConfigurationProperties(prefix = "unify")
public class UnifyProperties {

    /** 默认的映射 DSL 位置。 */
    public static final String DEFAULT_MAPPING_LOCATION = "classpath*:unify/mappings/**/*.json";

    /** 是否启用自动装配。 */
    private boolean enabled = true;

    /**
     * 启动时预注册的映射 DSL 位置，支持 Spring 资源通配符。每个文件一个映射，必须声明 name；
     * 任何一个文件不合法都会导致启动失败。
     */
    private List<String> mappingLocations = new ArrayList<>(List.of(DEFAULT_MAPPING_LOCATION));

    /** {@code transform(sourceJson, dslJson)} 缓存的已编译 DSL 个数，0 表示不缓存。 */
    private int cacheSize = JsonTransformer.DEFAULT_CACHE_SIZE;

    /** 是否通过 {@link java.util.ServiceLoader} 加载 classpath 中声明的 FunctionProvider。 */
    private boolean discoverFunctions = true;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public List<String> getMappingLocations() {
        return mappingLocations;
    }

    public void setMappingLocations(List<String> mappingLocations) {
        this.mappingLocations = mappingLocations;
    }

    public int getCacheSize() {
        return cacheSize;
    }

    public void setCacheSize(int cacheSize) {
        this.cacheSize = cacheSize;
    }

    public boolean isDiscoverFunctions() {
        return discoverFunctions;
    }

    public void setDiscoverFunctions(boolean discoverFunctions) {
        this.discoverFunctions = discoverFunctions;
    }
}
