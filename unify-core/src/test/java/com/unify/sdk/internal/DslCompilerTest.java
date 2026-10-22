package com.unify.sdk.internal;

import com.unify.sdk.CompiledMapping;
import com.unify.sdk.ErrorCode;
import com.unify.sdk.JsonTransformer;
import com.unify.sdk.TransformException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** DSL 编译期校验：错误码、出错位置、错误信息。 */
class DslCompilerTest {

    private final JsonTransformer transformer = JsonTransformer.create();

    private TransformException compileError(String dsl) {
        return assertThrows(TransformException.class, () -> transformer.compile(dsl));
    }

    private void assertError(String dsl, ErrorCode code, String location, String reasonPart) {
        TransformException e = compileError(dsl);
        assertEquals(code, e.errorCode(), e.getMessage());
        assertEquals(location, e.location(), e.getMessage());
        assertTrue(e.reason().contains(reasonPart), "错误信息应包含 [" + reasonPart + "]，实际：" + e.getMessage());
    }

    @Test
    void acceptsCommentsAndTrailingCommas() {
        CompiledMapping mapping = transformer.compile("""
                {
                  // 映射说明
                  "name": "demo",
                  "description": "演示",
                  /* 输出模板 */
                  "mapping": { "id": "$.id", },
                }
                """);
        assertEquals("demo", mapping.name());
        assertEquals("演示", mapping.description());
    }

    @Test
    void rejectsMalformedDocuments() {
        assertEquals(ErrorCode.INVALID_DSL, compileError("{").errorCode());
        assertEquals(ErrorCode.INVALID_DSL, compileError("").errorCode());
        assertError("[]", ErrorCode.INVALID_DSL, "", "DSL 必须是 JSON 对象");
        assertError("{\"name\": \"x\"}", ErrorCode.INVALID_DSL, "", "缺少 mapping");
        assertError("{\"mappings\": {}}", ErrorCode.INVALID_DSL, "/mappings", "未知的顶层字段");
        assertError("{\"name\": \" \", \"mapping\": {}}", ErrorCode.INVALID_DSL, "/name", "非空字符串");
    }

    @Test
    void validatesOptions() {
        assertError("{\"options\": [], \"mapping\": {}}", ErrorCode.INVALID_DSL, "/options", "JSON 对象");
        assertError("{\"options\": {\"omitNulls\": \"yes\"}, \"mapping\": {}}",
                ErrorCode.INVALID_DSL, "/options/omitNulls", "true 或 false");
        assertError("{\"options\": {\"strict\": true}, \"mapping\": {}}",
                ErrorCode.INVALID_DSL, "/options/strict", "未知的选项");
    }

    @Test
    void reportsInvalidPathsWithLocation() {
        assertError("{\"mapping\": {\"x\": \"$.a..b\"}}", ErrorCode.INVALID_DSL, "/mapping/x", "路径 $.a..b 无效");
        assertError("{\"mapping\": {\"a/b\": {\"c~d\": \"$.[\"}}}", ErrorCode.INVALID_DSL, "/mapping/a~1b/c~0d", "无效");
        assertError("{\"mapping\": {\"list\": [\"$.ok\", \"$.bad[\"]}}", ErrorCode.INVALID_DSL, "/mapping/list/1", "无效");
    }

    @Test
    void reportsExpressionErrors() {
        assertError("{\"mapping\": {\"x\": \"=upper($.a\"}}", ErrorCode.INVALID_DSL, "/mapping/x", "缺少 ')'");
        assertError("{\"mapping\": {\"x\": \"=upper($.a $.b)\"}}", ErrorCode.INVALID_DSL, "/mapping/x", "以 ',' 分隔");
        assertError("{\"mapping\": {\"x\": \"=abc\"}}", ErrorCode.INVALID_DSL, "/mapping/x", "无法识别的标识符 abc");
        assertError("{\"mapping\": {\"x\": \"=upper('a)\"}}", ErrorCode.INVALID_DSL, "/mapping/x", "缺少结束引号");
        assertError("{\"mapping\": {\"x\": \"=upper($.a))\"}}", ErrorCode.INVALID_DSL, "/mapping/x", "多余的内容");
        assertError("{\"mapping\": {\"x\": \"=\"}}", ErrorCode.INVALID_DSL, "/mapping/x", "表达式不完整");
        assertError("{\"mapping\": {\"x\": \"=1.2.3\"}}", ErrorCode.INVALID_DSL, "/mapping/x", "数字无效");
    }

    @Test
    void reportsUnknownFunctionsAndArity() {
        assertError("{\"name\": \"m1\", \"mapping\": {\"x\": \"=nope($.a)\"}}",
                ErrorCode.UNKNOWN_FUNCTION, "/mapping/x", "未知函数 nope");
        assertEquals("m1", compileError("{\"name\": \"m1\", \"mapping\": {\"x\": \"=nope()\"}}").mappingName());
        assertError("{\"mapping\": {\"x\": \"=upper($.a, $.b)\"}}",
                ErrorCode.INVALID_DSL, "/mapping/x", "函数 upper 需要 1 个参数，实际为 2 个");
        assertError("{\"mapping\": {\"x\": \"=substring($.a)\"}}",
                ErrorCode.INVALID_DSL, "/mapping/x", "需要 2 ~ 3 个参数");
        assertError("{\"mapping\": {\"x\": \"=concat()\"}}",
                ErrorCode.INVALID_DSL, "/mapping/x", "需要 至少 1 个参数");
    }

    @Test
    void validatesDirectives() {
        assertError("{\"mapping\": {\"s\": {\"$switch\": \"$.a\", \"$cases\": {}, \"x\": 1}}}",
                ErrorCode.INVALID_DSL, "/mapping/s/x", "不能出现普通字段");
        assertError("{\"mapping\": {\"s\": {\"$foo\": 1}}}",
                ErrorCode.INVALID_DSL, "/mapping/s", "未知的指令");
        assertError("{\"mapping\": {\"s\": {\"$const\": 1, \"$default\": 2}}}",
                ErrorCode.INVALID_DSL, "/mapping/s", "$const 不能与其他字段同时出现");
        assertError("{\"mapping\": {\"s\": {\"$if\": \"$.a\"}}}",
                ErrorCode.INVALID_DSL, "/mapping/s", "$then 或 $else");
        assertError("{\"mapping\": {\"s\": {\"$switch\": \"$.a\"}}}",
                ErrorCode.INVALID_DSL, "/mapping/s/$cases", "$cases");
        assertError("{\"mapping\": {\"s\": {\"$each\": \"$.a\", \"$then\": 1}}}",
                ErrorCode.INVALID_DSL, "/mapping/s/$then", "指令 $each 不支持 $then");
    }

    @Test
    void reportsNestedLocations() {
        assertError("""
                {"mapping": {"list": {"$each": "$.items", "$item": {"v": "=nope()"}}}}
                """, ErrorCode.UNKNOWN_FUNCTION, "/mapping/list/$item/v", "nope");
        assertError("""
                {"mapping": {"s": {"$switch": "$.a", "$cases": {"1": "=upper()"}}}}
                """, ErrorCode.INVALID_DSL, "/mapping/s/$cases/1", "upper");
        assertError("""
                {"mapping": {"s": {"$if": "$.a", "$then": "ok", "$default": "$.["}}}
                """, ErrorCode.INVALID_DSL, "/mapping/s/$default", "无效");
    }
}
