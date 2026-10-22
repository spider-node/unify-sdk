package com.unify.sdk.function;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.NullNode;
import com.unify.sdk.internal.Nodes;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * 函数的实参。参数已求值，空值统一为 JSON null；越界访问同样得到 JSON null。
 * <p>
 * 各个取值方法在参数为空时返回 {@code null}，类型无法转换时抛出 {@link IllegalArgumentException}。
 */
public final class FunctionArgs {

    private final String functionName;
    private final List<JsonNode> values;

    public FunctionArgs(String functionName, List<JsonNode> values) {
        this.functionName = functionName;
        List<JsonNode> copy = new ArrayList<>(values.size());
        for (JsonNode value : values) {
            copy.add(Nodes.orNull(value));
        }
        this.values = Collections.unmodifiableList(copy);
    }

    public String functionName() {
        return functionName;
    }

    public int size() {
        return values.size();
    }

    public List<JsonNode> values() {
        return values;
    }

    public JsonNode get(int index) {
        return index >= 0 && index < values.size() ? values.get(index) : NullNode.getInstance();
    }

    public boolean isNull(int index) {
        return Nodes.isNull(get(index));
    }

    /** 文本参数。数字、布尔值会转成文本；数组和对象视为类型错误。 */
    public String text(int index) {
        JsonNode node = get(index);
        if (Nodes.isNull(node)) {
            return null;
        }
        if (node.isContainerNode()) {
            throw error(index, "应为文本，实际为 " + Nodes.typeName(node));
        }
        return Nodes.text(node);
    }

    /** 数字参数。接受数字或数字文本，空白文本视为空值。 */
    public BigDecimal decimal(int index) {
        JsonNode node = get(index);
        if (Nodes.isNull(node)) {
            return null;
        }
        if (node.isNumber()) {
            return node.decimalValue();
        }
        if (node.isTextual()) {
            String text = node.textValue().strip();
            if (text.isEmpty()) {
                return null;
            }
            try {
                return new BigDecimal(text);
            } catch (NumberFormatException e) {
                throw error(index, "不是数字：" + node.textValue());
            }
        }
        throw error(index, "应为数字，实际为 " + Nodes.typeName(node));
    }

    public Integer integer(int index) {
        BigDecimal value = decimal(index);
        if (value == null) {
            return null;
        }
        try {
            return value.intValueExact();
        } catch (ArithmeticException e) {
            throw error(index, "应为整数：" + value.toPlainString());
        }
    }

    public Long longValue(int index) {
        BigDecimal value = decimal(index);
        if (value == null) {
            return null;
        }
        try {
            return value.longValueExact();
        } catch (ArithmeticException e) {
            throw error(index, "应为整数：" + value.toPlainString());
        }
    }

    /**
     * 布尔参数。接受布尔值；数字非 0 为真；文本 true/1/y/yes/on 为真、false/0/n/no/off 为假（不区分大小写），
     * 空白文本视为空值。
     */
    public Boolean bool(int index) {
        JsonNode node = get(index);
        if (Nodes.isNull(node)) {
            return null;
        }
        if (node.isBoolean()) {
            return node.booleanValue();
        }
        if (node.isNumber()) {
            return node.decimalValue().signum() != 0;
        }
        if (node.isTextual()) {
            String text = node.textValue().strip().toLowerCase(Locale.ROOT);
            switch (text) {
                case "":
                    return null;
                case "true":
                case "1":
                case "y":
                case "yes":
                case "on":
                    return Boolean.TRUE;
                case "false":
                case "0":
                case "n":
                case "no":
                case "off":
                    return Boolean.FALSE;
                default:
                    throw error(index, "不是布尔值：" + node.textValue());
            }
        }
        throw error(index, "应为布尔值，实际为 " + Nodes.typeName(node));
    }

    /** 列表参数。数组返回其元素；单个非空值视为只有一个元素的列表；空值返回 {@code null}。 */
    public List<JsonNode> list(int index) {
        JsonNode node = get(index);
        if (Nodes.isNull(node)) {
            return null;
        }
        if (!node.isArray()) {
            return List.of(node);
        }
        List<JsonNode> elements = new ArrayList<>(node.size());
        node.forEach(elements::add);
        return elements;
    }

    /** 创建返回值节点用的工厂。数字建议直接使用 {@code DecimalNode.valueOf} 以保留精度。 */
    public JsonNodeFactory nodes() {
        return JsonNodeFactory.instance;
    }

    /** 构造参数错误，消息形如"第 2 个参数不是数字：abc"。 */
    public IllegalArgumentException error(int index, String message) {
        return new IllegalArgumentException("第 " + (index + 1) + " 个参数" + message);
    }
}
