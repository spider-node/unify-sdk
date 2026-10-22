package com.unify.sdk.core.convert;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ConvertUtilsTest {

    enum Status { ACTIVE, FROZEN, UNKNOWN }

    @Test
    void codeMapperMapsKnownCodesAndFallsBackToDefault() {
        CodeMapper<String, Status> mapper = CodeMapper.<String, Status>builder("status")
                .map("1", Status.ACTIVE)
                .map("2", Status.FROZEN)
                .defaultValue(Status.UNKNOWN)
                .build();

        assertEquals(Status.ACTIVE, mapper.map("1"));
        assertEquals(Status.UNKNOWN, mapper.map("9"));
        assertEquals(Status.UNKNOWN, mapper.map(null));
    }

    @Test
    void strictCodeMapperRejectsUnknownCodes() {
        CodeMapper<String, Status> mapper = CodeMapper.<String, Status>builder("status")
                .map("1", Status.ACTIVE)
                .strict()
                .build();

        assertThrows(IllegalArgumentException.class, () -> mapper.map("9"));
    }

    @Test
    void conversionsAreNullSafe() {
        assertEquals(new BigDecimal("123.45"), Conversions.centsToYuan(12345L));
        assertEquals(12345L, Conversions.yuanToCents(new BigDecimal("123.45")));
        assertNull(Conversions.centsToYuan(null));
        assertNull(Conversions.parseDateTime("  ", "yyyyMMddHHmmss", ZoneId.of("Asia/Shanghai")));
        assertEquals(Instant.parse("2024-01-01T04:00:00Z"),
                Conversions.parseDateTime("20240101120000", "yyyyMMddHHmmss", ZoneId.of("Asia/Shanghai")));
        assertEquals(Boolean.TRUE, Conversions.flag("y"));
        assertEquals("b", Conversions.firstNonNull(null, "b", "c"));
    }
}
