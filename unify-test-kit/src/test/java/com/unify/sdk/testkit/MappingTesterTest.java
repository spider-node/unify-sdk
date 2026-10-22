package com.unify.sdk.testkit;

import com.fasterxml.jackson.databind.node.TextNode;
import com.unify.sdk.ErrorCode;
import com.unify.sdk.JsonTransformer;
import com.unify.sdk.TransformException;
import com.unify.sdk.function.TransformFunction;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MappingTesterTest {

    private static final String DSL = "testkit/order.dsl.json";
    private static final String SOURCE = "testkit/order.source.json";
    private static final String EXPECTED = "testkit/order.expected.json";
    private static final String WRONG = "testkit/order.wrong.json";

    @Test
    void verifiesDslFromClasspath() {
        MappingTester tester = MappingTester.withBuiltins();

        assertDoesNotThrow(() -> tester.verify(DSL, SOURCE, EXPECTED));
        assertDoesNotThrow(() -> tester.verify("/" + DSL, "/" + SOURCE, "/" + EXPECTED));
    }

    @Test
    void verifiesRegisteredMappingByName() {
        JsonTransformer transformer = JsonTransformer.builder()
                .mapping(MappingTester.readResource(DSL), DSL)
                .build();
        MappingTester tester = MappingTester.of(transformer);

        assertDoesNotThrow(() -> tester.verifyByName("order", SOURCE, EXPECTED));
        assertEquals("ALICE", tester.transformByName("order", SOURCE).get("buyer").asText());
    }

    @Test
    void failureNamesFilesAndDifferences() {
        MappingTester tester = MappingTester.withBuiltins();

        AssertionError error = assertThrows(AssertionError.class, () -> tester.verify(DSL, SOURCE, WRONG));

        String message = error.getMessage();
        assertTrue(message.startsWith("DSL：" + DSL + "，源数据：" + SOURCE + "，期望输出：" + WRONG + "\n"), message);
        assertTrue(message.contains("共 3 处差异"), message);
        assertTrue(message.contains("/buyer: 期望 \"alice\"，实际 \"ALICE\""), message);
        assertTrue(message.contains("/lines/1: 多出元素"), message);
        assertTrue(message.contains("/remark: 缺少字段，期望 null"), message);
    }

    @Test
    void usesCustomFunctionsOfGivenTransformer() {
        JsonTransformer transformer = JsonTransformer.builder()
                .function("tag", TransformFunction.of(1, 1, args -> TextNode.valueOf("#" + args.text(0))))
                .build();
        MappingTester tester = MappingTester.of(transformer);

        assertDoesNotThrow(() -> tester.verify("testkit/tagged.dsl.json", SOURCE, "testkit/tagged.expected.json"));
        assertThrows(TransformException.class,
                () -> MappingTester.withBuiltins().transform("testkit/tagged.dsl.json", SOURCE));
    }

    @Test
    void reportsMissingAndInvalidResources() {
        MappingTester tester = MappingTester.withBuiltins();

        IllegalArgumentException missing = assertThrows(IllegalArgumentException.class,
                () -> tester.verify("testkit/none.json", SOURCE, EXPECTED));
        assertEquals("找不到 classpath 资源：testkit/none.json", missing.getMessage());

        TransformException invalidDsl = assertThrows(TransformException.class,
                () -> tester.transform(SOURCE, SOURCE));
        assertEquals(ErrorCode.INVALID_DSL, invalidDsl.errorCode());
    }
}
