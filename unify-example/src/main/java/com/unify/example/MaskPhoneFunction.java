package com.unify.example;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.NullNode;
import com.unify.sdk.function.FunctionArgs;
import com.unify.sdk.function.TransformFunction;

/**
 * 自定义函数示例：手机号脱敏。{@code maskPhone('13812345678')} → {@code 138****5678}。
 * <p>
 * 长度不足 7 位时全部替换为 {@code *}，空值返回 null。
 */
public class MaskPhoneFunction implements TransformFunction {

    @Override
    public JsonNode apply(FunctionArgs args) {
        String phone = args.text(0);
        if (phone == null || phone.isBlank()) {
            return NullNode.getInstance();
        }
        String text = phone.strip();
        String masked = text.length() < 7
                ? "*".repeat(text.length())
                : text.substring(0, 3) + "****" + text.substring(text.length() - 4);
        return args.nodes().textNode(masked);
    }

    @Override
    public int minArgs() {
        return 1;
    }

    @Override
    public int maxArgs() {
        return 1;
    }
}
