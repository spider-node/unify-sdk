package com.unify.sdk.internal;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import static com.unify.sdk.TestJson.assertJson;
import static com.unify.sdk.TestJson.json;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JsonPathTest {

    private static final JsonNode SOURCE = json("""
            {
              "id": 7,
              "profile": { "name": "张三", "first name": "San", "tags": ["vip", "new"] },
              "orders": [
                { "no": "A1", "amount": 10 },
                { "no": "A2" },
                { "no": "A3", "amount": 30 }
              ],
              "empty": null
            }
            """);

    private static JsonNode eval(String path) {
        return JsonPath.parse(path).evaluate(SOURCE, SOURCE);
    }

    @Test
    void readsFieldsAndIndexes() {
        assertJson("7", eval("$.id"));
        assertJson("\"张三\"", eval("$.profile.name"));
        assertJson("\"San\"", eval("$.profile['first name']"));
        assertJson("\"San\"", eval("$.profile[\"first name\"]"));
        assertJson("\"vip\"", eval("$.profile.tags[0]"));
        assertJson("\"new\"", eval("$.profile.tags[-1]"));
        assertJson("\"A3\"", eval("$.orders[2].no"));
    }

    @Test
    void wholeDocument() {
        assertJson(SOURCE, eval("$"));
        assertJson(SOURCE, eval("$$"));
    }

    @Test
    void missingPathsYieldNull() {
        assertTrue(eval("$.nope").isNull());
        assertTrue(eval("$.empty.x").isNull());
        assertTrue(eval("$.profile.tags[5]").isNull());
        assertTrue(eval("$.id.x").isNull());
        assertTrue(eval("$.orders.no").isNull());
    }

    @Test
    void projectionsDropNulls() {
        assertJson("[\"A1\", \"A2\", \"A3\"]", eval("$.orders[*].no"));
        assertJson("[10, 30]", eval("$.orders[*].amount"));
        assertJson("[]", eval("$.nope[*].amount"));
        assertJson("[\"vip\", \"new\"]", eval("$.profile.tags.*"));
    }

    @Test
    void rootAndCurrentAreDistinct() {
        JsonNode current = SOURCE.get("orders").get(0);
        assertJson("\"A1\"", JsonPath.parse("$.no").evaluate(SOURCE, current));
        assertJson("7", JsonPath.parse("$$.id").evaluate(SOURCE, current));
    }

    @Test
    void parsesPrefixForExpressions() {
        JsonPath.Parsed parsed = JsonPath.parse("$.a.b, 'x')", 0);
        assertEquals("$.a.b", parsed.path().expression());
        assertEquals(5, parsed.end());
    }

    @Test
    void rejectsInvalidSyntax() {
        assertThrows(IllegalArgumentException.class, () -> JsonPath.parse("a.b"));
        assertThrows(IllegalArgumentException.class, () -> JsonPath.parse("$."));
        assertThrows(IllegalArgumentException.class, () -> JsonPath.parse("$.a["));
        assertThrows(IllegalArgumentException.class, () -> JsonPath.parse("$.a[x]"));
        assertThrows(IllegalArgumentException.class, () -> JsonPath.parse("$.a['b"));
        assertThrows(IllegalArgumentException.class, () -> JsonPath.parse("$.a b"));
    }
}
