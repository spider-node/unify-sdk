package com.unify.sdk.internal;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.BooleanNode;
import com.fasterxml.jackson.databind.node.DecimalNode;
import com.fasterxml.jackson.databind.node.IntNode;
import com.fasterxml.jackson.databind.node.LongNode;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.TextNode;

import java.math.BigDecimal;
import java.util.Locale;

/**
 * JSON 节点工具：空值判断、真值判断、比较用的规范化文本、节点构造。
 * <p>
 * 引擎内部约定：规则与函数的结果可能是 {@code null}、{@link NullNode} 或 MissingNode，
 * 三者都视为"空"，写入输出前统一转换为 {@link NullNode}。
 */
public final class Nodes {

    private Nodes() {
    }

    public static boolean isNull(JsonNode node) {
        return node == null || node.isNull() || node.isMissingNode();
    }

    public static JsonNode orNull(JsonNode node) {
        return isNull(node) ? NullNode.getInstance() : node;
    }

    /** 空值、空白文本、空数组、空对象。 */
    public static boolean isEmpty(JsonNode node) {
        if (isNull(node)) {
            return true;
        }
        if (node.isTextual()) {
            return node.textValue().isBlank();
        }
        return node.isContainerNode() && node.size() == 0;
    }

    /**
     * 条件判断使用的真值规则：空值、{@code false}、数字 0、空字符串、空数组、空对象为假，其余为真。
     */
    public static boolean truthy(JsonNode node) {
        if (isNull(node)) {
            return false;
        }
        if (node.isBoolean()) {
            return node.booleanValue();
        }
        if (node.isNumber()) {
            return node.decimalValue().signum() != 0;
        }
        if (node.isTextual()) {
            return !node.textValue().isEmpty();
        }
        if (node.isContainerNode()) {
            return node.size() > 0;
        }
        return true;
    }

    /** 节点的文本形式，数字保留原有精度且不使用科学计数法，容器节点返回 JSON 文本。空值返回 {@code null}。 */
    public static String text(JsonNode node) {
        if (isNull(node)) {
            return null;
        }
        if (node.isTextual()) {
            return node.textValue();
        }
        if (node.isNumber()) {
            return node.isIntegralNumber() ? node.asText() : node.decimalValue().toPlainString();
        }
        if (node.isValueNode()) {
            return node.asText();
        }
        return node.toString();
    }

    /**
     * 用于码值映射、相等比较的规范化文本：数字去掉末尾的 0（{@code 1}、{@code 1.0}、{@code 1.00} 都得到 {@code "1"}），
     * 文本保持原样。空值返回 {@code null}。
     */
    public static String key(JsonNode node) {
        if (isNull(node)) {
            return null;
        }
        if (node.isNumber()) {
            BigDecimal value = node.decimalValue().stripTrailingZeros();
            return value.toPlainString();
        }
        return text(node);
    }

    /** 宽松相等：两边都为空视为相等；标量按 {@link #key(JsonNode)} 比较；容器按结构比较。 */
    public static boolean sameValue(JsonNode a, JsonNode b) {
        boolean aNull = isNull(a);
        boolean bNull = isNull(b);
        if (aNull || bNull) {
            return aNull && bNull;
        }
        if (a.isContainerNode() || b.isContainerNode()) {
            return a.equals(b);
        }
        return key(a).equals(key(b));
    }

    public static String typeName(JsonNode node) {
        return isNull(node) ? "null" : node.getNodeType().name().toLowerCase(Locale.ROOT);
    }

    public static JsonNode textNode(String value) {
        return value == null ? NullNode.getInstance() : TextNode.valueOf(value);
    }

    public static JsonNode decimal(BigDecimal value) {
        if (value == null) {
            return NullNode.getInstance();
        }
        // 去掉末尾 0 后可能出现负数 scale（例如 1E+2），统一转回普通写法
        return DecimalNode.valueOf(value.scale() < 0 ? value.setScale(0) : value);
    }

    public static JsonNode integral(Long value) {
        if (value == null) {
            return NullNode.getInstance();
        }
        long v = value;
        return v >= Integer.MIN_VALUE && v <= Integer.MAX_VALUE ? IntNode.valueOf((int) v) : LongNode.valueOf(v);
    }

    public static JsonNode bool(Boolean value) {
        return value == null ? NullNode.getInstance() : BooleanNode.valueOf(value);
    }
}
