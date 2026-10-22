package com.unify.sdk.testkit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.MissingNode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

/**
 * JSON 结构比较。数字按数值比较（{@code 1} 与 {@code 1.0} 相等），对象字段与顺序无关，数组按位置比较，
 * 字段值为 null 与字段缺失视为不同。
 */
public final class JsonDiff {

    private JsonDiff() {
    }

    /**
     * 比较两个 JSON，返回全部差异，形如 {@code /orders/0/amount: 期望 19.99，实际 19.9}。没有差异时返回空列表。
     */
    public static List<String> diff(JsonNode expected, JsonNode actual) {
        List<String> differences = new ArrayList<>();
        compare("", orMissing(expected), orMissing(actual), differences);
        return Collections.unmodifiableList(differences);
    }

    /** 不一致时抛出 {@link AssertionError}，信息中列出全部差异与实际输出。 */
    public static void assertEquals(JsonNode expected, JsonNode actual) {
        assertEquals(expected, actual, null);
    }

    /**
     * @param context 附加在错误信息开头的说明，可为 {@code null}
     */
    public static void assertEquals(JsonNode expected, JsonNode actual, String context) {
        List<String> differences = diff(expected, actual);
        if (differences.isEmpty()) {
            return;
        }
        StringBuilder message = new StringBuilder();
        if (context != null) {
            message.append(context).append('\n');
        }
        message.append("JSON 不一致，共 ").append(differences.size()).append(" 处差异：\n");
        for (String difference : differences) {
            message.append("  ").append(difference).append('\n');
        }
        message.append("实际输出：\n").append(actual == null ? "null" : actual.toPrettyString());
        throw new AssertionError(message.toString());
    }

    private static void compare(String pointer, JsonNode expected, JsonNode actual, List<String> out) {
        if (expected.isObject() && actual.isObject()) {
            Iterator<String> names = expected.fieldNames();
            while (names.hasNext()) {
                String name = names.next();
                String child = pointer + "/" + escape(name);
                if (actual.has(name)) {
                    compare(child, expected.get(name), actual.get(name), out);
                } else {
                    out.add(child + ": 缺少字段，期望 " + expected.get(name));
                }
            }
            Iterator<String> actualNames = actual.fieldNames();
            while (actualNames.hasNext()) {
                String name = actualNames.next();
                if (!expected.has(name)) {
                    out.add(pointer + "/" + escape(name) + ": 多出字段，实际 " + actual.get(name));
                }
            }
            return;
        }
        if (expected.isArray() && actual.isArray()) {
            int common = Math.min(expected.size(), actual.size());
            for (int i = 0; i < common; i++) {
                compare(pointer + "/" + i, expected.get(i), actual.get(i), out);
            }
            for (int i = common; i < expected.size(); i++) {
                out.add(pointer + "/" + i + ": 缺少元素，期望 " + expected.get(i));
            }
            for (int i = common; i < actual.size(); i++) {
                out.add(pointer + "/" + i + ": 多出元素，实际 " + actual.get(i));
            }
            return;
        }
        boolean same = expected.isNumber() && actual.isNumber()
                ? expected.decimalValue().compareTo(actual.decimalValue()) == 0
                : expected.equals(actual);
        if (!same) {
            out.add((pointer.isEmpty() ? "/" : pointer) + ": 期望 " + display(expected) + "，实际 " + display(actual));
        }
    }

    private static JsonNode orMissing(JsonNode node) {
        return node == null ? MissingNode.getInstance() : node;
    }

    private static String display(JsonNode node) {
        return node.isMissingNode() ? "<无>" : node.toString();
    }

    private static String escape(String name) {
        return name.replace("~", "~0").replace("/", "~1");
    }
}
