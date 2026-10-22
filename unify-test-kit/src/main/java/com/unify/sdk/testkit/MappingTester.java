package com.unify.sdk.testkit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.unify.sdk.CompiledMapping;
import com.unify.sdk.JsonTransformer;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * 映射测试工具：从 classpath 读取 DSL、源数据与期望输出，执行转换并逐字段比较。
 *
 * <pre>{@code
 * MappingTester tester = MappingTester.of(transformer);
 * tester.verify("mappings/customer.json", "samples/customer-source.json", "expected/customer.json");
 * }</pre>
 *
 * 不依赖任何测试框架，断言失败时抛出 {@link AssertionError}，信息中包含差异路径与实际输出。
 */
public final class MappingTester {

    private final JsonTransformer transformer;

    private MappingTester(JsonTransformer transformer) {
        this.transformer = Objects.requireNonNull(transformer, "transformer");
    }

    /** 使用给定转换器（含自定义函数与预注册映射）创建测试工具。 */
    public static MappingTester of(JsonTransformer transformer) {
        return new MappingTester(transformer);
    }

    /** 使用只包含内置函数的转换器创建测试工具。 */
    public static MappingTester withBuiltins() {
        return new MappingTester(JsonTransformer.create());
    }

    public JsonTransformer transformer() {
        return transformer;
    }

    /**
     * 用 classpath 上的 DSL 转换源数据，并与期望输出比较。
     *
     * @param dslPath      DSL 文件路径，如 {@code mappings/customer.json}
     * @param sourcePath   源数据文件路径
     * @param expectedPath 期望输出文件路径
     */
    public void verify(String dslPath, String sourcePath, String expectedPath) {
        JsonNode actual = transform(dslPath, sourcePath);
        JsonDiff.assertEquals(readJson(expectedPath), actual,
                "DSL：" + dslPath + "，源数据：" + sourcePath + "，期望输出：" + expectedPath);
    }

    /** 用已注册的映射转换源数据，并与期望输出比较。 */
    public void verifyByName(String mappingName, String sourcePath, String expectedPath) {
        JsonNode actual = transformByName(mappingName, sourcePath);
        JsonDiff.assertEquals(readJson(expectedPath), actual,
                "映射：" + mappingName + "，源数据：" + sourcePath + "，期望输出：" + expectedPath);
    }

    /** 用 classpath 上的 DSL 转换 classpath 上的源数据，返回转换结果。 */
    public JsonNode transform(String dslPath, String sourcePath) {
        CompiledMapping mapping = transformer.compile(readResource(dslPath));
        return mapping.apply(readJson(sourcePath));
    }

    /** 用已注册的映射转换 classpath 上的源数据，返回转换结果。 */
    public JsonNode transformByName(String mappingName, String sourcePath) {
        return transformer.mapping(mappingName).apply(readJson(sourcePath));
    }

    /** 读取 classpath 资源并按转换器的 {@code ObjectMapper} 解析为 JSON。 */
    public JsonNode readJson(String path) {
        String text = readResource(path);
        try {
            return transformer.objectMapper().readTree(text);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("classpath 资源不是合法的 JSON：" + path + "，" + e.getOriginalMessage(), e);
        }
    }

    /** 以 UTF-8 读取 classpath 资源全文。路径开头的 {@code /} 会被忽略。 */
    public static String readResource(String path) {
        Objects.requireNonNull(path, "path");
        String normalized = path.startsWith("/") ? path.substring(1) : path;
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        if (loader == null) {
            loader = MappingTester.class.getClassLoader();
        }
        InputStream in = loader.getResourceAsStream(normalized);
        if (in == null && loader != MappingTester.class.getClassLoader()) {
            in = MappingTester.class.getClassLoader().getResourceAsStream(normalized);
        }
        if (in == null) {
            throw new IllegalArgumentException("找不到 classpath 资源：" + path);
        }
        try (InputStream stream = in) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("读取 classpath 资源失败：" + path, e);
        }
    }
}
