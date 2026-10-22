package com.unify.example;

import com.unify.sdk.JsonTransformer;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 示例映射的装配：注册自定义函数，预注册 classpath 中的映射文件。
 * 使用 Spring Boot 时由 starter 自动完成，见 {@code unify-spring-boot-starter}。
 */
public final class CustomerMappings {

    /** 老 CRM 客户 → 统一客户。 */
    public static final String LEGACY_CUSTOMER = "legacy-customer";
    /** 会员中心会员 → 统一客户。 */
    public static final String MEMBER_CUSTOMER = "member-customer";

    static final List<String> MAPPING_FILES = List.of(
            "mappings/legacy-customer.json",
            "mappings/member-customer.json");

    private CustomerMappings() {
    }

    public static JsonTransformer createTransformer() {
        JsonTransformer.Builder builder = JsonTransformer.builder()
                .function("maskPhone", new MaskPhoneFunction());
        for (String file : MAPPING_FILES) {
            builder.mapping(readResource(file), file);
        }
        return builder.build();
    }

    static String readResource(String path) {
        try (InputStream in = CustomerMappings.class.getClassLoader().getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalStateException("找不到 classpath 资源：" + path);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("读取 classpath 资源失败：" + path, e);
        }
    }
}
