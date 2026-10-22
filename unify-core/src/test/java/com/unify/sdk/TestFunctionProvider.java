package com.unify.sdk;

import com.fasterxml.jackson.databind.node.TextNode;
import com.unify.sdk.function.FunctionProvider;
import com.unify.sdk.function.TransformFunction;

import java.util.Map;

/** 通过 ServiceLoader 发现的测试函数，见 META-INF/services。 */
public class TestFunctionProvider implements FunctionProvider {

    @Override
    public Map<String, TransformFunction> functions() {
        return Map.of("shout", TransformFunction.of(1, 1, args -> {
            String text = args.text(0);
            return text == null ? null : TextNode.valueOf(text + "!");
        }));
    }
}
