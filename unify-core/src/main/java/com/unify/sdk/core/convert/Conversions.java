package com.unify.sdk.core.convert;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * 适配器常用的空值安全转换工具：金额单位、时间格式与时区、字符串清洗等。
 */
public final class Conversions {

    private Conversions() {
    }

    /** 分 → 元。 */
    public static BigDecimal centsToYuan(Long cents) {
        return cents == null ? null : BigDecimal.valueOf(cents).movePointLeft(2);
    }

    /** 元 → 分，要求精度不超过两位小数。 */
    public static Long yuanToCents(BigDecimal yuan) {
        return yuan == null ? null : yuan.movePointRight(2).longValueExact();
    }

    /** 毫秒时间戳 → Instant。 */
    public static Instant epochMillis(Long millis) {
        return millis == null ? null : Instant.ofEpochMilli(millis);
    }

    /** 秒级时间戳 → Instant。 */
    public static Instant epochSeconds(Long seconds) {
        return seconds == null ? null : Instant.ofEpochSecond(seconds);
    }

    /** 按格式和时区解析"本地日期时间"字符串，例如 {@code ("20240101120000", "yyyyMMddHHmmss", Asia/Shanghai)}。 */
    public static Instant parseDateTime(String text, String pattern, ZoneId zone) {
        String value = trimToNull(text);
        if (value == null) {
            return null;
        }
        return LocalDateTime.parse(value, DateTimeFormatter.ofPattern(pattern)).atZone(zone).toInstant();
    }

    /** 按格式解析日期字符串。 */
    public static LocalDate parseDate(String text, String pattern) {
        String value = trimToNull(text);
        return value == null ? null : LocalDate.parse(value, DateTimeFormatter.ofPattern(pattern));
    }

    public static String trimToNull(String text) {
        if (text == null) {
            return null;
        }
        String trimmed = text.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    public static String trimToEmpty(String text) {
        return text == null ? "" : text.trim();
    }

    /** "Y"/"1"/"true"（忽略大小写）视为 true，其余非空值视为 false，{@code null} 返回 {@code null}。 */
    public static Boolean flag(String text) {
        String value = trimToNull(text);
        if (value == null) {
            return null;
        }
        return "Y".equalsIgnoreCase(value) || "1".equals(value) || "true".equalsIgnoreCase(value);
    }

    @SafeVarargs
    public static <T> T firstNonNull(T... values) {
        if (values == null) {
            return null;
        }
        for (T value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }
}
