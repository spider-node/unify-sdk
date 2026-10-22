package com.unify.sdk.function.builtin;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.unify.sdk.ErrorCode;
import com.unify.sdk.JsonTransformer;
import com.unify.sdk.TestJson;
import com.unify.sdk.TransformException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static com.unify.sdk.TestJson.assertJson;
import static com.unify.sdk.TestJson.json;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BuiltinFunctionsTest {

    private static final String DATA = """
            {
              "arr": [1, 2, 3],
              "obj": {"a": 1},
              "empty": [],
              "dup": [1, 1.0, "1", "a", "a", null, null],
              "messy": [null, "", " ", "a", [], {}, 0, false],
              "nested": [[1, 2], [3], 4],
              "list": ["a", 1, null, true],
              "nums": [1, 2.0]
            }
            """;

    private final JsonTransformer transformer = JsonTransformer.create();

    /** 以表达式作为整个 mapping 求值。 */
    private JsonNode eval(String expression) {
        ObjectNode dsl = TestJson.MAPPER.createObjectNode().put("mapping", "=" + expression);
        return transformer.transform(json(DATA), dsl);
    }

    private void check(String expectedJson, String expression) {
        assertJson(expectedJson, eval(expression));
    }

    private TransformException fails(String expression) {
        TransformException e = assertThrows(TransformException.class, () -> eval(expression));
        assertEquals(ErrorCode.FUNCTION_FAILED, e.errorCode(), e.getMessage());
        return e;
    }

    @Test
    void allDocumentedFunctionsAreRegistered() {
        assertEquals(48, new BuiltinFunctions().functions().size());
        assertTrue(transformer.functionNames().containsAll(new BuiltinFunctions().functions().keySet()));
    }

    @Test
    void stringFunctions() {
        check("\"a\"", "trim(' a ')");
        check("null", "trim(null)");
        check("null", "trimToNull('   ')");
        check("\"x\"", "trimToNull(' x ')");
        check("\"ABC\"", "upper('abc')");
        check("\"abc\"", "lower('ABC')");
        check("\"a1true1.50\"", "concat('a', null, 1, true, 1.50)");
        check("null", "concat(null, $.missing)");
        check("\"el\"", "substring('hello', 1, 3)");
        check("\"lo\"", "substring('hello', 3)");
        check("\"hello\"", "substring('hello', -2, 99)");
        check("\"\"", "substring('hello', 4, 2)");
        check("\"a/b/c\"", "replace('a-b-c', '-', '/')");
        check("\"ab\"", "replace('a.b', '.', null)");
        check("[\"a\", \"b\", \"c\"]", "split('a, b,,c ,', ',')");
        check("[\"a\", \"b\"]", "split('a||b', '||')");
        check("null", "split(null, ',')");
        check("\"a-1-true\"", "join($.list, '-')");
        check("\"1.50\"", "toString(1.50)");
        check("\"{\\\"a\\\":1}\"", "toString($.obj)");
        fails("split('a', '')");
        fails("join($.nested, ',')");
        fails("upper($.obj)");
    }

    @Test
    void logicFunctions() {
        check("\"x\"", "default(null, 'x')");
        check("\"\"", "default('', 'x')");
        check("0", "coalesce(null, $.missing, 0, 1)");
        check("true", "isNull($.missing)");
        check("true", "isEmpty('  ')");
        check("true", "isEmpty($.empty)");
        check("false", "isEmpty(0)");
        check("true", "eq(1, 1.0)");
        check("true", "eq('1', 1)");
        check("true", "eq(null, $.missing)");
        check("true", "eq($.obj, $.obj)");
        check("true", "ne('a', 'b')");
        check("true", "gt('10', 9)");
        check("false", "gt(null, 1)");
        check("true", "lte(1.0, 1)");
        check("false", "lt(2, 1)");
        check("true", "gte(2, 2)");
        check("true", "and(1, 'x', true, $.arr)");
        check("false", "and(1, 0)");
        check("false", "and(1, $.empty)");
        check("true", "or(0, '', null, 'x')");
        check("false", "or(0, '', null, false)");
        check("true", "not('')");
        check("true", "in('b', 'a', 'b')");
        check("true", "in(2, $.nums)");
        check("false", "in('z', 'a')");
        check("\"y\"", "if(true, 'y', 'n')");
        check("null", "if(0, 'y')");
        check("true", "toBoolean('YES')");
        check("false", "toBoolean('off')");
        check("false", "toBoolean(0)");
        check("null", "toBoolean(' ')");
        fails("gt('abc', 1)");
        fails("toBoolean('maybe')");
    }

    @Test
    void numberFunctions() {
        JsonNode twelve = eval("toNumber('12')");
        assertTrue(twelve.isIntegralNumber());
        assertEquals(12, twelve.intValue());
        assertEquals(new BigDecimal("12.50"), eval("toNumber('12.50')").decimalValue());
        check("null", "toNumber(' ')");
        check("7", "toNumber(7)");
        check("12", "toInt('12.9')");
        check("-12", "toInt(-12.9)");
        check("2.35", "round(2.345, 2)");
        check("3", "round(2.5)");
        check("-3", "round(-2.5)");
        check("1200", "round(1234, -2)");
        assertEquals(new BigDecimal("0.3"), eval("add(0.1, 0.2)").decimalValue());
        check("6", "add(1, 2, 3)");
        check("null", "add(1, null)");
        check("9.99", "subtract(10, 0.01)");
        check("3", "multiply(1.5, 2)");
        check("2.5", "divide(10, 4)");
        check("3.33", "divide(10, 3, 2)");
        assertEquals(new BigDecimal("0.3333333333333333"), eval("divide(1, 3)").decimalValue());
        JsonNode hundred = eval("divide(100, 1)");
        assertTrue(hundred.isIntegralNumber(), hundred.toString());
        assertEquals(100, hundred.intValue());
        check("123.45", "centsToYuan(12345)");
        assertEquals("1.00", eval("centsToYuan('100')").decimalValue().toPlainString());
        check("12346", "yuanToCents(123.456)");
        check("10", "yuanToCents('0.1')");
        fails("toNumber('abc')");
        fails("divide(1, 0)");
        fails("add(1, true)");
    }

    @Test
    void dateFunctions() {
        check("\"2024-03-01T08:30:00+08:00\"",
                "parseDateTime('2024-03-01 08:30:00', 'yyyy-MM-dd HH:mm:ss', 'Asia/Shanghai')");
        check("\"2024-03-01T00:00:00Z\"", "parseDateTime('2024-03-01', 'yyyy-MM-dd')");
        check("\"2024-03-01T08:30:00+08:00\"",
                "parseDateTime('2024-03-01T08:30:00+0800', \"yyyy-MM-dd'T'HH:mm:ssZ\")");
        check("null", "parseDateTime(' ', 'yyyy-MM-dd')");
        check("\"2024-03-01\"", "parseDate('01/03/2024', 'dd/MM/yyyy')");
        check("\"2024-03-01 08:30\"", "formatDateTime('2024-03-01T00:30:00Z', 'yyyy-MM-dd HH:mm', 'Asia/Shanghai')");
        check("\"2024-03-01 00:30\"", "formatDateTime('2024-03-01T00:30:00+08:00', 'yyyy-MM-dd HH:mm')");
        check("\"1970-01-01\"", "formatDateTime(0, 'yyyy-MM-dd')");
        check("\"2024年03月01日\"", "formatDateTime('2024-03-01', 'yyyy年MM月dd日')");
        check("\"1970-01-01T00:00:00Z\"", "epochMillisToDateTime(0)");
        check("\"2024-03-01T00:00:00Z\"", "epochMillisToDateTime(1709251200000)");
        check("\"2024-03-01T08:00:00+08:00\"", "epochSecondsToDateTime(1709251200, 'Asia/Shanghai')");
        check("1709251200000", "dateTimeToEpochMillis('2024-03-01T08:00:00+08:00')");
        check("1709251200000", "dateTimeToEpochMillis('2024-03-01T08:00:00', 'Asia/Shanghai')");
        fails("parseDateTime('2024/03/01', 'yyyy-MM-dd')");
        fails("dateTimeToEpochMillis('2024-03-01 08:00:00')");
        fails("epochMillisToDateTime(0, 'Mars/Base')");
        fails("formatDateTime(0, 'yyyy-MM-dd {')");
    }

    @Test
    void collectionFunctions() {
        check("3", "size($.arr)");
        check("3", "size('abc')");
        check("0", "size(null)");
        check("1", "size($.obj)");
        check("1", "first($.arr)");
        check("3", "last($.arr)");
        check("null", "first($.empty)");
        check("\"x\"", "first('x')");
        check("[1, \"1\", \"a\", null]", "distinct($.dup)");
        check("[\"a\", 0, false]", "compact($.messy)");
        check("[1, 2, 3, 4]", "flatten($.nested)");
        check("true", "contains($.arr, 2.0)");
        check("false", "contains($.arr, 9)");
        check("true", "contains('hello', 'ell')");
        check("true", "contains($.obj, 'a')");
        check("false", "contains(null, 1)");
        fails("size(1)");
    }

    @Test
    void failureMessagesNameTheFunctionAndArgument() {
        TransformException e = fails("divide(1, 0)");
        assertEquals("函数 divide 执行失败：第 2 个参数除数不能为 0", e.reason());
        assertEquals("/mapping", e.location());
    }
}
