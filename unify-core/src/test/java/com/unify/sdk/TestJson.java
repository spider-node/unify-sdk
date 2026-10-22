package com.unify.sdk;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Comparator;

import static org.junit.jupiter.api.Assertions.fail;

/** 测试用 JSON 工具：解析、按数值比较数字的结构比较。 */
public final class TestJson {

    public static final ObjectMapper MAPPER = JsonTransformer.defaultObjectMapper();

    /** 数字按数值比较（1 与 1.0 相等），其余按 equals。 */
    private static final Comparator<JsonNode> NUMERIC_AWARE = (a, b) -> {
        if (a.isNumber() && b.isNumber()) {
            return a.decimalValue().compareTo(b.decimalValue());
        }
        return a.equals(b) ? 0 : 1;
    };

    private TestJson() {
    }

    public static JsonNode json(String text) {
        try {
            return MAPPER.readTree(text);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("测试数据不是合法 JSON：" + text, e);
        }
    }

    public static void assertJson(String expected, JsonNode actual) {
        assertJson(json(expected), actual);
    }

    public static void assertJson(String expected, String actual) {
        assertJson(json(expected), json(actual));
    }

    public static void assertJson(JsonNode expected, JsonNode actual) {
        if (actual == null || !expected.equals(NUMERIC_AWARE, actual)) {
            fail("JSON 不一致\n期望：" + pretty(expected) + "\n实际：" + pretty(actual));
        }
    }

    private static String pretty(JsonNode node) {
        try {
            return node == null ? "<null>" : MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(node);
        } catch (JsonProcessingException e) {
            return String.valueOf(node);
        }
    }
}
