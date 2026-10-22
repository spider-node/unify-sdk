package com.unify.sdk;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.StreamWriteFeature;
import com.fasterxml.jackson.core.json.JsonReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.cfg.JsonNodeFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.unify.sdk.function.FunctionProvider;
import com.unify.sdk.function.TransformFunction;
import com.unify.sdk.function.builtin.BuiltinFunctions;
import com.unify.sdk.internal.DslCompiler;
import com.unify.sdk.internal.ExprParser;
import com.unify.sdk.internal.MappingDefinition;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.TreeSet;

/**
 * SDK 入口：传入源数据 JSON 与映射 DSL，输出目标 JSON。
 *
 * <pre>{@code
 * JsonTransformer transformer = JsonTransformer.create();
 * String output = transformer.transform(sourceJson, dslJson);
 *
 * // 预注册映射，按名称调用
 * JsonTransformer transformer = JsonTransformer.builder()
 *         .function("maskPhone", new MaskPhoneFunction())
 *         .mapping(legacyCustomerDsl)
 *         .build();
 * String output = transformer.transformByName("legacy-customer", sourceJson);
 * }</pre>
 *
 * 实例不可变、线程安全，建议全局复用。DSL 文本允许使用 Java 风格注释与末尾逗号。
 */
public final class JsonTransformer {

    /** {@link #transform(String, String)} 默认缓存的 DSL 个数。 */
    public static final int DEFAULT_CACHE_SIZE = 256;

    private final ObjectMapper objectMapper;
    private final ObjectReader dslReader;
    private final Map<String, TransformFunction> functions;
    private final DslCompiler compiler;
    private final Map<String, CompiledMapping> mappings;
    private final Map<String, CompiledMapping> cache;

    private JsonTransformer(Builder builder, Map<String, TransformFunction> functions) {
        this.objectMapper = builder.objectMapper == null ? defaultObjectMapper() : builder.objectMapper;
        this.dslReader = objectMapper.reader()
                .with(JsonReadFeature.ALLOW_JAVA_COMMENTS)
                .with(JsonReadFeature.ALLOW_TRAILING_COMMA);
        this.functions = Collections.unmodifiableMap(functions);
        this.compiler = new DslCompiler(functions);
        this.cache = builder.cacheSize > 0 ? Collections.synchronizedMap(new LruCache(builder.cacheSize)) : null;
        Map<String, CompiledMapping> named = new LinkedHashMap<>();
        for (PendingMapping pending : builder.mappings) {
            CompiledMapping mapping = compilePending(pending);
            if (mapping.name() == null) {
                throw new TransformException(ErrorCode.INVALID_DSL, null, "",
                        "预注册的映射必须声明 name" + pending.originSuffix(), null);
            }
            if (named.putIfAbsent(mapping.name(), mapping) != null) {
                throw new IllegalStateException("映射名称重复：" + mapping.name() + pending.originSuffix());
            }
        }
        this.mappings = Collections.unmodifiableMap(named);
    }

    public static Builder builder() {
        return new Builder();
    }

    /** 只带内置函数、没有预注册映射的实例。 */
    public static JsonTransformer create() {
        return builder().build();
    }

    /**
     * SDK 默认使用的 ObjectMapper：小数按 {@link java.math.BigDecimal} 读取并保留末尾的 0，输出时不使用科学计数法，
     * 拒绝 JSON 之后的多余内容。
     */
    public static ObjectMapper defaultObjectMapper() {
        return JsonMapper.builder()
                .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
                .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                .enable(StreamWriteFeature.WRITE_BIGDECIMAL_AS_PLAIN)
                .disable(JsonNodeFeature.STRIP_TRAILING_BIGDECIMAL_ZEROES)
                .build();
    }

    /** 编译 DSL。结果可以缓存起来反复使用。 */
    public CompiledMapping compile(String dslJson) {
        return compile(readDsl(dslJson));
    }

    /** 编译 DSL 树。 */
    public CompiledMapping compile(JsonNode dsl) {
        MappingDefinition definition = compiler.compile(dsl);
        return new CompiledMapping(definition, objectMapper);
    }

    /**
     * 用临时 DSL 转换源数据。编译结果按 DSL 文本缓存（见 {@link Builder#cacheSize(int)}）。
     *
     * @param sourceJson 源数据 JSON
     * @param dslJson    映射 DSL
     * @return 目标 JSON
     */
    public String transform(String sourceJson, String dslJson) {
        return compileCached(dslJson).apply(sourceJson);
    }

    /** 用临时 DSL 转换源数据，不使用缓存。 */
    public JsonNode transform(JsonNode source, JsonNode dsl) {
        return compile(dsl).apply(source);
    }

    /** 用预注册的映射转换源数据。 */
    public String transformByName(String mappingName, String sourceJson) {
        return mapping(mappingName).apply(sourceJson);
    }

    /** 用预注册的映射转换源数据。 */
    public JsonNode transformByName(String mappingName, JsonNode source) {
        return mapping(mappingName).apply(source);
    }

    /**
     * 获取预注册的映射。
     *
     * @throws TransformException 不存在时错误码为 {@link ErrorCode#MAPPING_NOT_FOUND}
     */
    public CompiledMapping mapping(String mappingName) {
        CompiledMapping mapping = mappingName == null ? null : mappings.get(mappingName);
        if (mapping == null) {
            throw new TransformException(ErrorCode.MAPPING_NOT_FOUND, mappingName, null,
                    "映射不存在，已注册的映射：" + (mappings.isEmpty() ? "无" : String.join("、", mappingNames())), null);
        }
        return mapping;
    }

    public boolean hasMapping(String mappingName) {
        return mappingName != null && mappings.containsKey(mappingName);
    }

    /** 预注册映射的名称，按字母排序。 */
    public Set<String> mappingNames() {
        return Collections.unmodifiableSet(new TreeSet<>(mappings.keySet()));
    }

    /** 可用函数（内置与自定义）的名称，按字母排序。 */
    public Set<String> functionNames() {
        return Collections.unmodifiableSet(new TreeSet<>(functions.keySet()));
    }

    public ObjectMapper objectMapper() {
        return objectMapper;
    }

    private CompiledMapping compileCached(String dslJson) {
        if (cache == null || dslJson == null) {
            return compile(dslJson);
        }
        CompiledMapping cached = cache.get(dslJson);
        if (cached == null) {
            cached = compile(dslJson);
            cache.put(dslJson, cached);
        }
        return cached;
    }

    private CompiledMapping compilePending(PendingMapping pending) {
        try {
            JsonNode dsl = pending.tree != null ? pending.tree : readDsl(pending.json);
            return compile(dsl);
        } catch (TransformException e) {
            if (pending.origin == null) {
                throw e;
            }
            TransformException withOrigin = new TransformException(e.errorCode(), e.mappingName(), e.location(),
                    e.reason() + pending.originSuffix(), e.getCause());
            withOrigin.setStackTrace(e.getStackTrace());
            throw withOrigin;
        }
    }

    private JsonNode readDsl(String dslJson) {
        if (dslJson == null) {
            throw new TransformException(ErrorCode.INVALID_DSL, "DSL 不能为空");
        }
        JsonNode dsl;
        try {
            dsl = dslReader.readTree(dslJson);
        } catch (JsonProcessingException e) {
            throw new TransformException(ErrorCode.INVALID_DSL, null, null,
                    "DSL 不是合法的 JSON：" + e.getOriginalMessage(), e);
        }
        if (dsl == null || dsl.isMissingNode()) {
            throw new TransformException(ErrorCode.INVALID_DSL, "DSL 不能为空");
        }
        return dsl;
    }

    /** {@link JsonTransformer} 的构建器，非线程安全。 */
    public static final class Builder {

        private final Map<String, TransformFunction> functions = new LinkedHashMap<>();
        private final List<PendingMapping> mappings = new ArrayList<>();
        private ObjectMapper objectMapper;
        private int cacheSize = DEFAULT_CACHE_SIZE;

        private Builder() {
        }

        /**
         * 注册自定义函数。函数名只能包含字母、数字、下划线且不能以数字开头，不能与内置函数或已注册的函数重名。
         */
        public Builder function(String name, TransformFunction function) {
            if (!ExprParser.isValidFunctionName(name)) {
                throw new IllegalArgumentException("函数名无效：" + name + "，只能包含字母、数字、下划线且不能以数字开头");
            }
            if (function == null) {
                throw new IllegalArgumentException("函数 " + name + " 的实现不能为空");
            }
            if (functions.putIfAbsent(name, function) != null) {
                throw new IllegalStateException("函数重复注册：" + name);
            }
            return this;
        }

        /** 注册一组函数。 */
        public Builder functions(FunctionProvider provider) {
            if (provider == null) {
                throw new IllegalArgumentException("FunctionProvider 不能为空");
            }
            Map<String, TransformFunction> provided = provider.functions();
            if (provided != null) {
                provided.forEach(this::function);
            }
            return this;
        }

        /** 通过 {@link ServiceLoader} 加载类路径上的全部 {@link FunctionProvider}。 */
        public Builder discoverFunctions() {
            ClassLoader loader = Thread.currentThread().getContextClassLoader();
            if (loader == null) {
                loader = JsonTransformer.class.getClassLoader();
            }
            for (FunctionProvider provider : ServiceLoader.load(FunctionProvider.class, loader)) {
                functions(provider);
            }
            return this;
        }

        /** 预注册一个映射，DSL 必须声明 name。DSL 在 {@link #build()} 时编译。 */
        public Builder mapping(String dslJson) {
            return mapping(dslJson, null);
        }

        /**
         * 预注册一个映射。
         *
         * @param origin DSL 的来源描述（例如文件路径），编译出错时附在错误信息中，可为 {@code null}
         */
        public Builder mapping(String dslJson, String origin) {
            if (dslJson == null) {
                throw new IllegalArgumentException("DSL 不能为空");
            }
            mappings.add(new PendingMapping(dslJson, null, origin));
            return this;
        }

        /** 预注册一个映射，DSL 必须声明 name。 */
        public Builder mapping(JsonNode dsl) {
            if (dsl == null) {
                throw new IllegalArgumentException("DSL 不能为空");
            }
            mappings.add(new PendingMapping(null, dsl.deepCopy(), null));
            return this;
        }

        /** 自定义 ObjectMapper，用于解析源数据、DSL 以及输出结果。默认见 {@link JsonTransformer#defaultObjectMapper()}。 */
        public Builder objectMapper(ObjectMapper objectMapper) {
            this.objectMapper = objectMapper;
            return this;
        }

        /**
         * {@link JsonTransformer#transform(String, String)} 缓存的 DSL 个数，0 表示不缓存。
         * 默认 {@value JsonTransformer#DEFAULT_CACHE_SIZE}。
         */
        public Builder cacheSize(int cacheSize) {
            if (cacheSize < 0) {
                throw new IllegalArgumentException("cacheSize 不能小于 0：" + cacheSize);
            }
            this.cacheSize = cacheSize;
            return this;
        }

        /**
         * 构建实例并编译全部预注册的映射。
         *
         * @throws TransformException    预注册的映射有误
         * @throws IllegalStateException 函数与内置函数重名，或映射名称重复
         */
        public JsonTransformer build() {
            Map<String, TransformFunction> all = new LinkedHashMap<>(new BuiltinFunctions().functions());
            for (Map.Entry<String, TransformFunction> entry : functions.entrySet()) {
                if (all.containsKey(entry.getKey())) {
                    throw new IllegalStateException("函数 " + entry.getKey() + " 与内置函数重名");
                }
                all.put(entry.getKey(), entry.getValue());
            }
            return new JsonTransformer(this, all);
        }
    }

    private record PendingMapping(String json, JsonNode tree, String origin) {

        String originSuffix() {
            return origin == null ? "" : "（来源：" + origin + "）";
        }
    }

    private static final class LruCache extends LinkedHashMap<String, CompiledMapping> {

        private final int maxSize;

        LruCache(int maxSize) {
            super(16, 0.75f, true);
            this.maxSize = maxSize;
        }

        @Override
        protected boolean removeEldestEntry(Map.Entry<String, CompiledMapping> eldest) {
            return size() > maxSize;
        }
    }
}
