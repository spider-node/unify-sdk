package com.unify.example;

import com.unify.sdk.ErrorCode;
import com.unify.sdk.JsonTransformer;
import com.unify.sdk.TransformException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MaskPhoneFunctionTest {

    private static final String DSL = "{\"mapping\": \"=maskPhone($.phone)\"}";

    private final JsonTransformer transformer = JsonTransformer.builder()
            .function("maskPhone", new MaskPhoneFunction())
            .build();

    private String mask(String source) {
        return transformer.transform(source, DSL);
    }

    @Test
    void masksMiddleDigits() {
        assertEquals("\"138****5678\"", mask("{\"phone\": \"13812345678\"}"));
        assertEquals("\"138****5678\"", mask("{\"phone\": \" 13812345678 \"}"));
        assertEquals("\"138****5678\"", mask("{\"phone\": 13812345678}"));
        assertEquals("\"021****4567\"", mask("{\"phone\": \"021-1234567\"}"));
    }

    @Test
    void masksShortNumbersEntirely() {
        assertEquals("\"*****\"", mask("{\"phone\": \"12345\"}"));
    }

    @Test
    void returnsNullForMissingOrBlank() {
        assertEquals("null", mask("{}"));
        assertEquals("null", mask("{\"phone\": null}"));
        assertEquals("null", mask("{\"phone\": \"  \"}"));
    }

    @Test
    void checksArgumentCountWhenCompiling() {
        TransformException e = assertThrows(TransformException.class,
                () -> transformer.compile("{\"mapping\": \"=maskPhone($.a, $.b)\"}"));

        assertEquals(ErrorCode.INVALID_DSL, e.errorCode());
        assertEquals("/mapping", e.location());
    }
}
