package com.unify.sdk.api;

public enum SourceStatus {

    SUCCESS,

    FAILED,

    TIMEOUT,

    /** 未实际调用，例如快速失败后被取消，或调用前已超过截止时间。 */
    SKIPPED
}
