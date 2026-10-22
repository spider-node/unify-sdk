package com.unify.sdk;

/**
 * SDK 抛出的唯一异常类型。
 * <p>
 * 除错误码外，还会尽量给出映射名称与出错位置。位置是 DSL 中的 JSON Pointer，例如 {@code /mapping/balance}，
 * 可以直接据此定位到 DSL 里的哪一个字段。
 */
public class TransformException extends RuntimeException {

    private final ErrorCode errorCode;
    private final String mappingName;
    private final String location;
    private final String reason;

    public TransformException(ErrorCode errorCode, String reason) {
        this(errorCode, null, null, reason, null);
    }

    /**
     * @param errorCode   错误码
     * @param mappingName 映射名称，匿名映射为 {@code null}
     * @param location    DSL 中的出错位置（JSON Pointer），未知时为 {@code null}
     * @param reason      错误原因
     * @param cause       原始异常
     */
    public TransformException(ErrorCode errorCode, String mappingName, String location, String reason,
            Throwable cause) {
        super(format(errorCode, mappingName, location, reason), cause);
        this.errorCode = errorCode;
        this.mappingName = mappingName;
        this.location = location;
        this.reason = reason;
    }

    public ErrorCode errorCode() {
        return errorCode;
    }

    public String mappingName() {
        return mappingName;
    }

    public String location() {
        return location;
    }

    /** 不含错误码、映射名、位置前缀的原因描述。 */
    public String reason() {
        return reason;
    }

    private static String format(ErrorCode errorCode, String mappingName, String location, String reason) {
        StringBuilder message = new StringBuilder("[").append(errorCode).append("] ");
        if (mappingName != null) {
            message.append("映射 ").append(mappingName).append(' ');
        }
        if (location != null) {
            message.append("位置 ").append(location.isEmpty() ? "/" : location).append(' ');
        }
        if (mappingName != null || location != null) {
            message.setLength(message.length() - 1);
            message.append("：");
        }
        return message.append(reason).toString();
    }
}
