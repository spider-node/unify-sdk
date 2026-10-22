package com.unify.sdk.testkit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unify.sdk.JsonTransformer;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JsonDiffTest {

    private static final ObjectMapper MAPPER = JsonTransformer.defaultObjectMapper();

    private static JsonNode json(String text) {
        try {
            return MAPPER.readTree(text);
        } catch (Exception e) {
            throw new IllegalArgumentException(e);
        }
    }

    @Test
    void equalDocumentsHaveNoDifferences() {
        JsonNode expected = json("{\"a\":1,\"b\":[1,2,{\"c\":\"x\"}],\"d\":null}");
        JsonNode actual = json("{\"d\":null,\"b\":[1.0,2.00,{\"c\":\"x\"}],\"a\":1.000}");

        assertEquals(List.of(), JsonDiff.diff(expected, actual));
        assertDoesNotThrow(() -> JsonDiff.assertEquals(expected, actual));
    }

    @Test
    void reportsEveryDifferenceWithPointer() {
        JsonNode expected = json("{\"a\":1,\"list\":[1,2],\"gone\":true,\"a/b\":\"x\"}");
        JsonNode actual = json("{\"a\":2,\"list\":[1,2,3],\"extra\":\"y\",\"a/b\":\"x\"}");

        assertEquals(List.of(
                "/a: 期望 1，实际 2",
                "/list/2: 多出元素，实际 3",
                "/gone: 缺少字段，期望 true",
                "/extra: 多出字段，实际 \"y\""), JsonDiff.diff(expected, actual));
    }

    @Test
    void escapesPointerSegments() {
        assertEquals(List.of("/a~1b/c~0d: 期望 1，实际 2"),
                JsonDiff.diff(json("{\"a/b\":{\"c~d\":1}}"), json("{\"a/b\":{\"c~d\":2}}")));
    }

    @Test
    void nullDiffersFromMissingAndFromOtherTypes() {
        assertEquals(List.of("/a: 缺少字段，期望 null"), JsonDiff.diff(json("{\"a\":null}"), json("{}")));
        assertEquals(List.of("/a: 期望 null，实际 0"), JsonDiff.diff(json("{\"a\":null}"), json("{\"a\":0}")));
        assertEquals(List.of("/a: 期望 \"1\"，实际 1"), JsonDiff.diff(json("{\"a\":\"1\"}"), json("{\"a\":1}")));
        assertEquals(List.of("/list/0: 缺少元素，期望 1"), JsonDiff.diff(json("{\"list\":[1]}"), json("{\"list\":[]}")));
    }

    @Test
    void comparesRootAndNullArguments() {
        assertEquals(List.of("/: 期望 [1]，实际 {}"), JsonDiff.diff(json("[1]"), json("{}")));
        assertEquals(List.of("/: 期望 1，实际 <无>"), JsonDiff.diff(json("1"), null));
        assertEquals(List.of(), JsonDiff.diff(null, null));
    }

    @Test
    void assertionMessageListsDifferencesAndActualOutput() {
        AssertionError error = assertThrows(AssertionError.class,
                () -> JsonDiff.assertEquals(json("{\"a\":1}"), json("{\"a\":2}"), "用例：示例"));

        String message = error.getMessage();
        assertTrue(message.startsWith("用例：示例\nJSON 不一致，共 1 处差异：\n  /a: 期望 1，实际 2\n"), message);
        assertTrue(message.contains("实际输出：\n"), message);
        assertTrue(message.contains("\"a\" : 2"), message);
    }
}
