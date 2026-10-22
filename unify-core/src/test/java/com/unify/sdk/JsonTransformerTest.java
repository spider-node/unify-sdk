package com.unify.sdk;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.unify.sdk.function.TransformFunction;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static com.unify.sdk.TestJson.assertJson;
import static com.unify.sdk.TestJson.json;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JsonTransformerTest {

    private static final String LEGACY_SOURCE = """
            {
              "CUST_ID": 10086,
              "CUST_NAME": "  张三 ",
              "MOBILE": "13812345678",
              "STATUS": "1",
              "BAL_FEN": 12345,
              "REG_TIME": "2023-05-06 14:30:00",
              "TAGS": "vip, new ,",
              "ADDR": { "PROV": "广东", "CITY": "深圳" },
              "ORDERS": [
                { "ORDER_NO": "A001", "AMT_FEN": 1999, "STATE": 2 },
                { "ORDER_NO": "A002", "AMT_FEN": 500, "STATE": 9 },
                { "ORDER_NO": "A003", "AMT_FEN": 0, "STATE": 1 }
              ]
            }
            """;

    private static final String LEGACY_DSL = """
            {
              "name": "legacy-customer",
              "description": "旧核心系统客户 → 统一客户",
              "mapping": {
                "id": "=toString($.CUST_ID)",
                "name": "=trim($.CUST_NAME)",
                "status": {
                  "$switch": "$.STATUS",
                  "$cases": { "1": "ACTIVE", "2": "FROZEN", "9": "CLOSED" },
                  "$default": "UNKNOWN"
                },
                "balance": "=centsToYuan($.BAL_FEN)",
                "registeredAt": "=parseDateTime($.REG_TIME, 'yyyy-MM-dd HH:mm:ss', '+08:00')",
                "tags": "=split($.TAGS, ',')",
                "address": {
                  "province": "$.ADDR.PROV",
                  "city": "$.ADDR.CITY",
                  "full": "=concat($.ADDR.PROV, $.ADDR.CITY)"
                },
                "orders": {
                  "$each": "$.ORDERS",
                  "$filter": "=ne($.STATE, 9)",
                  "$item": {
                    "orderNo": "$.ORDER_NO",
                    "amount": "=centsToYuan($.AMT_FEN)",
                    "paid": "=eq($.STATE, 2)",
                    "customerId": "$$.CUST_ID"
                  }
                },
                "orderCount": "=size($.ORDERS)",
                "vip": "=contains(split($.TAGS, ','), 'vip')",
                "source": "LEGACY",
                "remark": "$.REMARK"
              }
            }
            """;

    private static final String LEGACY_EXPECTED = """
            {
              "id": "10086",
              "name": "张三",
              "status": "ACTIVE",
              "balance": 123.45,
              "registeredAt": "2023-05-06T14:30:00+08:00",
              "tags": ["vip", "new"],
              "address": { "province": "广东", "city": "深圳", "full": "广东深圳" },
              "orders": [
                { "orderNo": "A001", "amount": 19.99, "paid": true, "customerId": 10086 },
                { "orderNo": "A003", "amount": 0.00, "paid": false, "customerId": 10086 }
              ],
              "orderCount": 3,
              "vip": true,
              "source": "LEGACY",
              "remark": null
            }
            """;

    private final JsonTransformer transformer = JsonTransformer.create();

    private JsonNode run(String dsl, String source) {
        return transformer.transform(json(source), json(dsl));
    }

    @Test
    void transformsLegacyCustomer() {
        assertJson(LEGACY_EXPECTED, transformer.transform(LEGACY_SOURCE, LEGACY_DSL));
        assertJson(LEGACY_EXPECTED, transformer.transform(json(LEGACY_SOURCE), json(LEGACY_DSL)));
        // 第二次调用命中缓存，结果一致
        assertJson(LEGACY_EXPECTED, transformer.transform(LEGACY_SOURCE, LEGACY_DSL));
    }

    @Test
    void compiledMappingIsReusable() {
        CompiledMapping mapping = transformer.compile(LEGACY_DSL);
        assertEquals("legacy-customer", mapping.name());
        assertEquals("旧核心系统客户 → 统一客户", mapping.description());
        assertJson(LEGACY_EXPECTED, mapping.apply(LEGACY_SOURCE));
        assertJson(LEGACY_EXPECTED, mapping.apply(json(LEGACY_SOURCE)));
    }

    @Test
    void constantsAndTemplates() {
        JsonNode out = run("""
                {"mapping": {
                  "text": "plain",
                  "number": 1.50,
                  "flag": false,
                  "nothing": null,
                  "literal": {"$const": {"$.notAPath": "=notAnExpression"}},
                  "nested": [1, "$.a", ["$.b", "x"]],
                  "obj": {"inner": {"a": "$.a"}}
                }}
                """, "{\"a\": 1, \"b\": \"two\"}");
        assertJson("""
                {
                  "text": "plain",
                  "number": 1.50,
                  "flag": false,
                  "nothing": null,
                  "literal": {"$.notAPath": "=notAnExpression"},
                  "nested": [1, 1, ["two", "x"]],
                  "obj": {"inner": {"a": 1}}
                }
                """, out);
    }

    @Test
    void mappingMayBeAnyJsonValue() {
        assertJson("[1, 2]", run("{\"mapping\": \"$.items[*].id\"}", "{\"items\": [{\"id\": 1}, {\"id\": 2}]}"));
        assertJson("\"x\"", run("{\"mapping\": \"=lower('X')\"}", "{}"));
        assertJson("[\"a\", null]", run("{\"mapping\": [\"$.a\", \"$.missing\"]}", "{\"a\": \"a\"}"));
    }

    @Test
    void switchMatchesNormalizedKeys() {
        String dsl = """
                {"mapping": {"s": {"$switch": "$.v", "$cases": {"1": "ONE", "2.5": "TWO_AND_HALF", "x": "=upper($.v)"}}}}
                """;
        assertJson("{\"s\": \"ONE\"}", run(dsl, "{\"v\": 1}"));
        assertJson("{\"s\": \"ONE\"}", run(dsl, "{\"v\": 1.00}"));
        assertJson("{\"s\": \"ONE\"}", run(dsl, "{\"v\": \"1\"}"));
        assertJson("{\"s\": \"TWO_AND_HALF\"}", run(dsl, "{\"v\": 2.50}"));
        assertJson("{\"s\": \"X\"}", run(dsl, "{\"v\": \"x\"}"));
        assertJson("{\"s\": null}", run(dsl, "{\"v\": \"other\"}"));
        assertJson("{\"s\": null}", run(dsl, "{}"));
    }

    @Test
    void eachHandlesArraysScalarsAndNulls() {
        String dsl = """
                {"mapping": {"list": {"$each": "$.items", "$item": {"v": "$.x", "root": "$$.tag"}, "$default": []}}}
                """;
        assertJson("{\"list\": [{\"v\": 1, \"root\": \"t\"}, {\"v\": 2, \"root\": \"t\"}]}",
                run(dsl, "{\"tag\": \"t\", \"items\": [{\"x\": 1}, {\"x\": 2}]}"));
        assertJson("{\"list\": [{\"v\": 3, \"root\": \"t\"}]}", run(dsl, "{\"tag\": \"t\", \"items\": {\"x\": 3}}"));
        assertJson("{\"list\": []}", run(dsl, "{\"tag\": \"t\"}"));
        assertJson("{\"list\": []}", run(dsl, "{\"items\": []}"));

        // 没有 $item 时原样输出元素；没有 $default 时空源得到 null
        assertJson("{\"list\": [2, 3]}", run("""
                {"mapping": {"list": {"$each": "$.n", "$filter": "=gt($, 1)"}}}
                """, "{\"n\": [1, 2, 3]}"));
        assertJson("{\"list\": null}", run("""
                {"mapping": {"list": {"$each": "$.n"}}}
                """, "{}"));
    }

    @Test
    void ifAndDefault() {
        String dsl = """
                {"mapping": {
                  "level": {"$if": "=gte($.score, 60)", "$then": "PASS", "$else": "FAIL"},
                  "bonus": {"$if": "$.vip", "$then": 100},
                  "nick": {"$if": "$.nick", "$then": "$.nick", "$default": "=concat('用户', $.id)"},
                  "status": {"$switch": "$.s", "$cases": {"1": "OK"}, "$default": "UNKNOWN"}
                }}
                """;
        assertJson("{\"level\": \"PASS\", \"bonus\": 100, \"nick\": \"小王\", \"status\": \"OK\"}",
                run(dsl, "{\"score\": 75, \"vip\": true, \"nick\": \"小王\", \"id\": 1, \"s\": 1}"));
        assertJson("{\"level\": \"FAIL\", \"bonus\": null, \"nick\": \"用户7\", \"status\": \"UNKNOWN\"}",
                run(dsl, "{\"score\": \"59.5\", \"vip\": false, \"nick\": \"\", \"id\": 7, \"s\": 9}"));
    }

    @Test
    void omitNullsDropsNullFieldsAndItems() {
        String dsl = """
                {
                  "options": {"omitNulls": true},
                  "mapping": {
                    "a": "$.a",
                    "missing": "$.missing",
                    "nested": {"x": "$.missing"},
                    "items": {"$each": "$.items", "$item": "$.v"},
                    "positional": ["$.a", "$.missing"]
                  }
                }
                """;
        assertJson("{\"a\": 1, \"nested\": {}, \"items\": [1, 3], \"positional\": [1, null]}",
                run(dsl, "{\"a\": 1, \"items\": [{\"v\": 1}, {}, {\"v\": 3}]}"));
    }

    @Test
    void nullsAreKeptByDefault() {
        assertJson("{\"a\": null, \"items\": [1, null]}", run("""
                {"mapping": {"a": "$.a", "items": {"$each": "$.items", "$item": "$.v"}}}
                """, "{\"items\": [{\"v\": 1}, {}]}"));
    }

    @Test
    void expressionLiteralsAndNesting() {
        assertJson("{\"v\": \"a,bc'd\\n\"}", run("""
                {"mapping": {"v": "=concat('a,b', \\"c'd\\", '\\\\n')"}}
                """, "{}"));
        assertJson("{\"v\": 3, \"w\": -1.5, \"t\": true, \"n\": null}", run("""
                {"mapping": {"v": "=add(1, 2)", "w": "=multiply(-0.5, 3)", "t": "=not(false)", "n": "=default(null, null)"}}
                """, "{}"));
        assertJson("{\"v\": \"ABC\"}", run("""
                {"mapping": {"v": "= upper( trim( $.s ) ) "}}
                """, "{\"s\": \" abc \"}"));
    }

    @Test
    void preservesDecimalPrecision() {
        String out = transformer.transform("{\"amount\": 0.10, \"big\": 12345678901234567890.123, \"id\": 9007199254740993}",
                "{\"mapping\": {\"amount\": \"$.amount\", \"big\": \"$.big\", \"id\": \"$.id\"}}");
        assertTrue(out.contains("\"amount\":0.10"), out);
        assertTrue(out.contains("\"big\":12345678901234567890.123"), out);
        assertTrue(out.contains("\"id\":9007199254740993"), out);
    }

    @Test
    void resultIsIsolatedFromSourceAndDsl() {
        JsonNode source = json("{\"profile\": {\"name\": \"a\"}}");
        CompiledMapping mapping = transformer.compile("{\"mapping\": {\"p\": \"$.profile\", \"c\": {\"$const\": {\"k\": 1}}}}");
        JsonNode first = mapping.apply(source);
        ((ObjectNode) first.get("p")).put("name", "b");
        ((ObjectNode) first.get("c")).put("k", 2);
        assertEquals("a", source.get("profile").get("name").asText());
        assertJson("{\"p\": {\"name\": \"a\"}, \"c\": {\"k\": 1}}", mapping.apply(source));
    }

    @Test
    void customFunctions() {
        JsonTransformer custom = JsonTransformer.builder()
                .function("maskPhone", TransformFunction.of(1, 1, args -> {
                    String phone = args.text(0);
                    return phone == null ? null : TextNode.valueOf(phone.substring(0, 3) + "****" + phone.substring(7));
                }))
                .discoverFunctions()
                .build();
        assertJson("{\"phone\": \"138****5678\", \"shout\": \"hi!\"}", custom.transform(json("{\"m\": \"13812345678\"}"),
                json("{\"mapping\": {\"phone\": \"=maskPhone($.m)\", \"shout\": \"=shout('hi')\"}}")));
        assertTrue(custom.functionNames().containsAll(Set.of("maskPhone", "shout", "upper")));
        assertFalse(transformer.functionNames().contains("maskPhone"));
    }

    @Test
    void functionsFromProvider() {
        JsonTransformer custom = JsonTransformer.builder()
                .functions(() -> Map.of("twice", TransformFunction.of(1, 1,
                        args -> TextNode.valueOf(args.text(0) + args.text(0)))))
                .build();
        assertJson("{\"v\": \"abab\"}", custom.transform(json("{}"), json("{\"mapping\": {\"v\": \"=twice('ab')\"}}")));
    }

    @Test
    void functionRegistrationIsValidated() {
        TransformFunction fn = args -> null;
        assertThrows(IllegalArgumentException.class, () -> JsonTransformer.builder().function("1abc", fn));
        assertThrows(IllegalArgumentException.class, () -> JsonTransformer.builder().function("a-b", fn));
        assertThrows(IllegalArgumentException.class, () -> JsonTransformer.builder().function("ok", null));
        assertThrows(IllegalStateException.class, () -> JsonTransformer.builder().function("ok", fn).function("ok", fn));
        assertThrows(IllegalStateException.class, () -> JsonTransformer.builder().function("upper", fn).build());
        assertThrows(IllegalArgumentException.class, () -> TransformFunction.of(2, 1, fn));
        assertThrows(IllegalArgumentException.class, () -> JsonTransformer.builder().cacheSize(-1));
    }

    @Test
    void functionFailuresCarryLocation() {
        JsonTransformer custom = JsonTransformer.builder()
                .function("boom", args -> {
                    throw new IllegalStateException("炸了");
                })
                .build();
        TransformException e = assertThrows(TransformException.class, () -> custom.transform("{}",
                "{\"name\": \"m\", \"mapping\": {\"list\": [{\"x\": \"=boom()\"}]}}"));
        assertEquals(ErrorCode.FUNCTION_FAILED, e.errorCode());
        assertEquals("m", e.mappingName());
        assertEquals("/mapping/list/0/x", e.location());
        assertEquals("函数 boom 执行失败：炸了", e.reason());
        assertInstanceOf(IllegalStateException.class, e.getCause());
        assertTrue(e.getMessage().startsWith("[FUNCTION_FAILED] 映射 m 位置 /mapping/list/0/x："), e.getMessage());

        TransformException divide = assertThrows(TransformException.class,
                () -> transformer.transform("{}", "{\"mapping\": {\"v\": \"=divide(1, 0)\"}}"));
        assertEquals(ErrorCode.FUNCTION_FAILED, divide.errorCode());
        assertTrue(divide.reason().contains("除数不能为 0"), divide.reason());
    }

    @Test
    void namedMappings() {
        JsonTransformer named = JsonTransformer.builder()
                .mapping(LEGACY_DSL)
                .mapping(json("{\"name\": \"echo\", \"mapping\": \"$\"}"))
                .build();
        assertEquals(Set.of("echo", "legacy-customer"), named.mappingNames());
        assertTrue(named.hasMapping("echo"));
        assertFalse(named.hasMapping("nope"));
        assertJson(LEGACY_EXPECTED, named.transformByName("legacy-customer", LEGACY_SOURCE));
        assertJson("{\"a\": 1}", named.transformByName("echo", json("{\"a\": 1}")));
        assertSame(named.mapping("echo"), named.mapping("echo"));

        TransformException e = assertThrows(TransformException.class, () -> named.transformByName("nope", "{}"));
        assertEquals(ErrorCode.MAPPING_NOT_FOUND, e.errorCode());
        assertEquals("nope", e.mappingName());
        assertTrue(e.reason().contains("echo、legacy-customer"), e.reason());
    }

    @Test
    void namedMappingsAreValidatedAtBuild() {
        TransformException noName = assertThrows(TransformException.class,
                () -> JsonTransformer.builder().mapping("{\"mapping\": {}}", "a.json").build());
        assertEquals(ErrorCode.INVALID_DSL, noName.errorCode());
        assertTrue(noName.reason().contains("必须声明 name") && noName.reason().contains("（来源：a.json）"),
                noName.reason());

        TransformException broken = assertThrows(TransformException.class,
                () -> JsonTransformer.builder().mapping("{\"name\": \"b\", \"mapping\": {\"x\": \"=nope()\"}}", "b.json").build());
        assertEquals(ErrorCode.UNKNOWN_FUNCTION, broken.errorCode());
        assertEquals("/mapping/x", broken.location());
        assertTrue(broken.reason().endsWith("（来源：b.json）"), broken.reason());

        TransformException badJson = assertThrows(TransformException.class,
                () -> JsonTransformer.builder().mapping("{", "c.json").build());
        assertEquals(ErrorCode.INVALID_DSL, badJson.errorCode());

        assertThrows(IllegalStateException.class, () -> JsonTransformer.builder()
                .mapping("{\"name\": \"dup\", \"mapping\": 1}")
                .mapping("{\"name\": \"dup\", \"mapping\": 2}")
                .build());
    }

    @Test
    void sourceHandling() {
        String dsl = "{\"mapping\": {\"a\": \"$.a\", \"all\": \"$\"}}";
        assertJson("{\"a\": null, \"all\": null}", transformer.transform(null, dsl));
        assertJson("{\"a\": null, \"all\": [1]}", transformer.transform("[1]", dsl));
        assertJson("{\"a\": null, \"all\": \"text\"}", transformer.transform("\"text\"", dsl));

        TransformException invalid = assertThrows(TransformException.class, () -> transformer.transform("{a:", dsl));
        assertEquals(ErrorCode.INVALID_SOURCE, invalid.errorCode());
        assertEquals(ErrorCode.INVALID_SOURCE,
                assertThrows(TransformException.class, () -> transformer.transform("{} {}", dsl)).errorCode());
        assertEquals(ErrorCode.INVALID_SOURCE,
                assertThrows(TransformException.class, () -> transformer.transform("", dsl)).errorCode());
        assertEquals(ErrorCode.INVALID_DSL,
                assertThrows(TransformException.class, () -> transformer.transform("{}", (String) null)).errorCode());
    }

    @Test
    void cacheCanBeDisabled() {
        JsonTransformer noCache = JsonTransformer.builder().cacheSize(0).build();
        assertJson(LEGACY_EXPECTED, noCache.transform(LEGACY_SOURCE, LEGACY_DSL));
        assertJson(LEGACY_EXPECTED, noCache.transform(LEGACY_SOURCE, LEGACY_DSL));

        JsonTransformer tiny = JsonTransformer.builder().cacheSize(1).build();
        for (int i = 0; i < 3; i++) {
            assertJson("{\"v\": " + i + "}", tiny.transform("{}", "{\"mapping\": {\"v\": " + i + "}}"));
        }
    }
}
