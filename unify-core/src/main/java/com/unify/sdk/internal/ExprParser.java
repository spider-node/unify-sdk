package com.unify.sdk.internal;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.BooleanNode;
import com.fasterxml.jackson.databind.node.DecimalNode;
import com.fasterxml.jackson.databind.node.IntNode;
import com.fasterxml.jackson.databind.node.LongNode;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.unify.sdk.ErrorCode;
import com.unify.sdk.TransformException;
import com.unify.sdk.function.TransformFunction;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 表达式解析器。语法：
 *
 * <pre>
 * expr    := path | literal | call
 * call    := name '(' [expr (',' expr)*] ')'
 * literal := 'text' | "text" | number | true | false | null
 * path    := 见 {@link JsonPath}
 * </pre>
 *
 * 字符串支持 {@code \\ \' \" \n \t} 转义。函数在解析时即解析为具体实现，并校验参数个数。
 */
public final class ExprParser {

    private final String text;
    private final Map<String, TransformFunction> functions;
    private final String mappingName;
    private final String location;
    private int pos;

    private ExprParser(String text, Map<String, TransformFunction> functions, String mappingName, String location) {
        this.text = text;
        this.functions = functions;
        this.mappingName = mappingName;
        this.location = location;
    }

    /**
     * @param text        不含前导 {@code =} 的表达式文本
     * @param functions   可用函数
     * @param mappingName 映射名称，用于错误信息
     * @param location    表达式在 DSL 中的位置，用于错误信息
     */
    public static Rule parse(String text, Map<String, TransformFunction> functions, String mappingName,
            String location) {
        ExprParser parser = new ExprParser(text, functions, mappingName, location);
        Rule rule = parser.expression();
        parser.skipSpaces();
        if (parser.pos < text.length()) {
            throw parser.error("多余的内容：" + text.substring(parser.pos));
        }
        return rule;
    }

    private Rule expression() {
        skipSpaces();
        if (pos >= text.length()) {
            throw error("表达式不完整");
        }
        char c = text.charAt(pos);
        if (c == '$') {
            try {
                JsonPath.Parsed parsed = JsonPath.parse(text, pos);
                pos = parsed.end();
                return new Rules.Path(parsed.path());
            } catch (IllegalArgumentException e) {
                throw error("路径无效：" + e.getMessage());
            }
        }
        if (c == '\'' || c == '"') {
            return new Rules.Const(TextNode.valueOf(string(c)));
        }
        if (c == '-' || Character.isDigit(c)) {
            return new Rules.Const(number());
        }
        if (isNameStart(c)) {
            int start = pos;
            String name = name();
            skipSpaces();
            if (pos < text.length() && text.charAt(pos) == '(') {
                return call(name, start);
            }
            switch (name) {
                case "true":
                    return new Rules.Const(BooleanNode.TRUE);
                case "false":
                    return new Rules.Const(BooleanNode.FALSE);
                case "null":
                    return new Rules.Const(NullNode.getInstance());
                default:
                    pos = start;
                    throw error("无法识别的标识符 " + name + "：字符串需要加引号，函数调用需要加括号");
            }
        }
        throw error("无法识别的字符 '" + c + "'");
    }

    private Rule call(String name, int start) {
        pos++; // '('
        List<Rule> args = new ArrayList<>();
        skipSpaces();
        if (pos < text.length() && text.charAt(pos) == ')') {
            pos++;
        } else {
            while (true) {
                args.add(expression());
                skipSpaces();
                if (pos >= text.length()) {
                    throw error("函数 " + name + " 缺少 ')'");
                }
                char c = text.charAt(pos);
                pos++;
                if (c == ')') {
                    break;
                }
                if (c != ',') {
                    pos--;
                    throw error("函数 " + name + " 的参数之间应以 ',' 分隔");
                }
            }
        }
        TransformFunction function = functions.get(name);
        if (function == null) {
            throw new TransformException(ErrorCode.UNKNOWN_FUNCTION, mappingName, location,
                    "未知函数 " + name + "（表达式：" + text + "）", null);
        }
        int min = function.minArgs();
        int max = function.maxArgs();
        if (args.size() < min || args.size() > max) {
            pos = start;
            String expected = min == max ? String.valueOf(min)
                    : max == Integer.MAX_VALUE ? "至少 " + min : min + " ~ " + max;
            throw error("函数 " + name + " 需要 " + expected + " 个参数，实际为 " + args.size() + " 个");
        }
        return new Rules.Call(name, function, args, location);
    }

    private String string(char quote) {
        int start = pos;
        pos++;
        StringBuilder value = new StringBuilder();
        while (pos < text.length()) {
            char c = text.charAt(pos++);
            if (c == quote) {
                return value.toString();
            }
            if (c != '\\') {
                value.append(c);
                continue;
            }
            if (pos >= text.length()) {
                break;
            }
            char escaped = text.charAt(pos++);
            switch (escaped) {
                case 'n':
                    value.append('\n');
                    break;
                case 't':
                    value.append('\t');
                    break;
                default:
                    value.append(escaped);
            }
        }
        pos = start;
        throw error("字符串缺少结束引号");
    }

    private JsonNode number() {
        int start = pos;
        if (text.charAt(pos) == '-') {
            pos++;
        }
        boolean integral = true;
        while (pos < text.length()) {
            char c = text.charAt(pos);
            if (Character.isDigit(c)) {
                pos++;
            } else if (c == '.' || c == 'e' || c == 'E') {
                integral = false;
                pos++;
            } else if ((c == '+' || c == '-') && (text.charAt(pos - 1) == 'e' || text.charAt(pos - 1) == 'E')) {
                pos++;
            } else {
                break;
            }
        }
        String literal = text.substring(start, pos);
        try {
            BigDecimal value = new BigDecimal(literal);
            if (integral) {
                long v = value.longValueExact();
                return v >= Integer.MIN_VALUE && v <= Integer.MAX_VALUE ? IntNode.valueOf((int) v) : LongNode.valueOf(v);
            }
            return DecimalNode.valueOf(value);
        } catch (NumberFormatException | ArithmeticException e) {
            pos = start;
            throw error("数字无效：" + literal);
        }
    }

    private String name() {
        int start = pos;
        while (pos < text.length() && isNamePart(text.charAt(pos))) {
            pos++;
        }
        return text.substring(start, pos);
    }

    private void skipSpaces() {
        while (pos < text.length() && Character.isWhitespace(text.charAt(pos))) {
            pos++;
        }
    }

    private TransformException error(String message) {
        return new TransformException(ErrorCode.INVALID_DSL, mappingName, location,
                "表达式 =" + text + " 第 " + (pos + 1) + " 个字符处：" + message, null);
    }

    static boolean isNameStart(char c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || c == '_';
    }

    static boolean isNamePart(char c) {
        return isNameStart(c) || (c >= '0' && c <= '9');
    }

    /** 函数名是否合法：字母或下划线开头，只包含字母、数字、下划线。 */
    public static boolean isValidFunctionName(String name) {
        if (name == null || name.isEmpty() || !isNameStart(name.charAt(0))) {
            return false;
        }
        for (int i = 1; i < name.length(); i++) {
            if (!isNamePart(name.charAt(i))) {
                return false;
            }
        }
        return true;
    }
}
