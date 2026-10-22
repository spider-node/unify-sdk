package com.unify.sdk.function.builtin;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.NullNode;
import com.unify.sdk.function.FunctionArgs;
import com.unify.sdk.function.TransformFunction;
import com.unify.sdk.internal.Nodes;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.TemporalAccessor;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static com.unify.sdk.function.builtin.BuiltinFunctions.define;

/**
 * 日期时间函数。
 * <ul>
 *     <li>日期时间统一输出为带偏移量的 ISO-8601 文本，例如 {@code 2024-03-01T08:30:00+08:00}；日期输出为 {@code 2024-03-01}。</li>
 *     <li>时区参数可选，例如 {@code 'Asia/Shanghai'}、{@code '+08:00'}，默认 UTC。</li>
 *     <li>格式使用 {@link DateTimeFormatter} 的模式语法，例如 {@code 'yyyy-MM-dd HH:mm:ss'}。</li>
 * </ul>
 */
final class DateFunctions {

    private static final ZoneId UTC = ZoneOffset.UTC;
    private static final int MAX_CACHED_FORMATTERS = 256;
    private static final Map<String, DateTimeFormatter> FORMATTERS = new ConcurrentHashMap<>();

    private DateFunctions() {
    }

    static void register(Map<String, TransformFunction> functions) {
        // parseDateTime(text, pattern[, zone])：按格式解析；文本不含时区时按 zone 解释，只有日期时取当天 0 点
        define(functions, "parseDateTime", 2, 3, args -> {
            String text = trimmed(args, 0);
            if (text == null) {
                return NullNode.getInstance();
            }
            DateTimeFormatter formatter = formatter(args, 1);
            ZoneId zone = zone(args, 2);
            TemporalAccessor parsed;
            try {
                parsed = formatter.parseBest(text, ZonedDateTime::from, LocalDateTime::from, LocalDate::from);
            } catch (DateTimeParseException e) {
                throw args.error(0, "无法按格式 " + args.text(1) + " 解析：" + text);
            }
            ZonedDateTime result;
            if (parsed instanceof ZonedDateTime zoned) {
                result = zoned;
            } else if (parsed instanceof LocalDateTime local) {
                result = local.atZone(zone);
            } else {
                result = ((LocalDate) parsed).atStartOfDay(zone);
            }
            return dateTime(result);
        });
        // parseDate(text, pattern)：按格式解析日期，输出 yyyy-MM-dd
        define(functions, "parseDate", 2, 2, args -> {
            String text = trimmed(args, 0);
            if (text == null) {
                return NullNode.getInstance();
            }
            DateTimeFormatter formatter = formatter(args, 1);
            try {
                return Nodes.textNode(LocalDate.parse(text, formatter).format(DateTimeFormatter.ISO_LOCAL_DATE));
            } catch (DateTimeParseException e) {
                throw args.error(0, "无法按格式 " + args.text(1) + " 解析：" + text);
            }
        });
        // formatDateTime(value, pattern[, zone])：value 可以是 ISO 日期时间、ISO 日期或毫秒时间戳；
        // 带偏移量或时间戳的值会换算到 zone，不带偏移量的值按 zone 解释
        define(functions, "formatDateTime", 2, 3, args -> {
            ZonedDateTime value = toZoned(args, 0, 2);
            if (value == null) {
                return NullNode.getInstance();
            }
            return Nodes.textNode(formatter(args, 1).format(value));
        });
        // epochMillisToDateTime(millis[, zone])
        define(functions, "epochMillisToDateTime", 1, 2, args -> {
            Long millis = args.longValue(0);
            return millis == null ? NullNode.getInstance()
                    : dateTime(Instant.ofEpochMilli(millis).atZone(zone(args, 1)));
        });
        // epochSecondsToDateTime(seconds[, zone])
        define(functions, "epochSecondsToDateTime", 1, 2, args -> {
            Long seconds = args.longValue(0);
            return seconds == null ? NullNode.getInstance()
                    : dateTime(Instant.ofEpochSecond(seconds).atZone(zone(args, 1)));
        });
        // dateTimeToEpochMillis(value[, zone])：value 同 formatDateTime；不带偏移量的值按 zone 解释
        define(functions, "dateTimeToEpochMillis", 1, 2, args -> {
            ZonedDateTime value = toZoned(args, 0, 1);
            return value == null ? NullNode.getInstance() : Nodes.integral(value.toInstant().toEpochMilli());
        });
    }

    private static JsonNode dateTime(ZonedDateTime value) {
        return Nodes.textNode(value.toOffsetDateTime().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
    }

    private static String trimmed(FunctionArgs args, int index) {
        String text = args.text(index);
        if (text == null || text.isBlank()) {
            return null;
        }
        return text.strip();
    }

    /** 把 ISO 文本或毫秒时间戳转为 {@link ZonedDateTime}；显式给出时区时，带偏移量的值换算到该时区。 */
    private static ZonedDateTime toZoned(FunctionArgs args, int valueIndex, int zoneIndex) {
        JsonNode value = args.get(valueIndex);
        if (Nodes.isNull(value)) {
            return null;
        }
        boolean zoneGiven = !args.isNull(zoneIndex);
        ZoneId zone = zone(args, zoneIndex);
        if (value.isNumber()) {
            return Instant.ofEpochMilli(args.longValue(valueIndex)).atZone(zone);
        }
        String text = trimmed(args, valueIndex);
        if (text == null) {
            return null;
        }
        try {
            OffsetDateTime offset = OffsetDateTime.parse(text, DateTimeFormatter.ISO_OFFSET_DATE_TIME);
            return zoneGiven ? offset.atZoneSameInstant(zone) : offset.toZonedDateTime();
        } catch (DateTimeParseException ignored) {
            // 继续尝试其他格式
        }
        try {
            return LocalDateTime.parse(text, DateTimeFormatter.ISO_LOCAL_DATE_TIME).atZone(zone);
        } catch (DateTimeParseException ignored) {
            // 继续尝试其他格式
        }
        try {
            return LocalDate.parse(text, DateTimeFormatter.ISO_LOCAL_DATE).atStartOfDay(zone);
        } catch (DateTimeParseException e) {
            throw args.error(valueIndex, "不是 ISO 日期时间或毫秒时间戳：" + text);
        }
    }

    private static ZoneId zone(FunctionArgs args, int index) {
        String text = trimmed(args, index);
        if (text == null) {
            return UTC;
        }
        try {
            return ZoneId.of(text);
        } catch (DateTimeException e) {
            throw args.error(index, "不是有效的时区：" + text);
        }
    }

    private static DateTimeFormatter formatter(FunctionArgs args, int index) {
        String pattern = args.text(index);
        if (pattern == null || pattern.isEmpty()) {
            throw args.error(index, "日期格式不能为空");
        }
        DateTimeFormatter cached = FORMATTERS.get(pattern);
        if (cached != null) {
            return cached;
        }
        DateTimeFormatter formatter;
        try {
            formatter = DateTimeFormatter.ofPattern(pattern, Locale.ROOT);
        } catch (IllegalArgumentException e) {
            throw args.error(index, "不是有效的日期格式：" + pattern + "（" + e.getMessage() + "）");
        }
        if (FORMATTERS.size() < MAX_CACHED_FORMATTERS) {
            FORMATTERS.putIfAbsent(pattern, formatter);
        }
        return formatter;
    }
}
