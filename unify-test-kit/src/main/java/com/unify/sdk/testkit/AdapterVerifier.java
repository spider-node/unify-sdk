package com.unify.sdk.testkit;

import com.unify.sdk.api.InvokeOptions;
import com.unify.sdk.core.InvocationContext;
import com.unify.sdk.core.SourceAdapter;

import java.util.Objects;

/**
 * 适配器校验器：脱离引擎和真实接口，单独验证一个适配器的路由判断与双向转换。
 * 断言失败时抛出 {@link AssertionError}，可在任意测试框架中使用。
 *
 * <pre>{@code
 * AdapterVerifier.of(new LegacyCrmAdapter())
 *         .assertSupports(new CustomerQuery("L1001"))
 *         .assertNotSupports(new CustomerQuery("M2001"))
 *         .assertSourceRequest(new CustomerQuery("L1001"), new LegacyCustomerRequest("L1001", "UNIFY"))
 *         .assertUnifiedResponse(new CustomerQuery("L1001"), legacyResponse, expectedProfile);
 * }</pre>
 */
public final class AdapterVerifier<Q, R, SQ, SR> {

    private final SourceAdapter<Q, R, SQ, SR> adapter;
    private InvocationContext context;

    private AdapterVerifier(SourceAdapter<Q, R, SQ, SR> adapter) {
        this.adapter = Objects.requireNonNull(adapter, "adapter");
        this.context = InvocationContext.create(adapter.capability().name(), "test-trace", InvokeOptions.defaults(), null);
    }

    public static <Q, R, SQ, SR> AdapterVerifier<Q, R, SQ, SR> of(SourceAdapter<Q, R, SQ, SR> adapter) {
        return new AdapterVerifier<>(adapter);
    }

    /** 使用指定的调用参数（例如租户属性）构造上下文。 */
    public AdapterVerifier<Q, R, SQ, SR> withOptions(InvokeOptions options) {
        this.context = InvocationContext.create(adapter.capability().name(), "test-trace", options, null);
        return this;
    }

    public InvocationContext context() {
        return context;
    }

    public boolean supports(Q request) {
        return adapter.supports(request, context);
    }

    public SQ toSourceRequest(Q request) {
        return adapter.toSourceRequest(request, context);
    }

    public R toUnifiedResponse(Q request, SR sourceResponse) {
        return adapter.toUnifiedResponse(sourceResponse, request, context);
    }

    public AdapterVerifier<Q, R, SQ, SR> assertSupports(Q request) {
        if (!supports(request)) {
            throw new AssertionError("Adapter for source '" + adapter.sourceId() + "' should support " + request);
        }
        return this;
    }

    public AdapterVerifier<Q, R, SQ, SR> assertNotSupports(Q request) {
        if (supports(request)) {
            throw new AssertionError("Adapter for source '" + adapter.sourceId() + "' should not support " + request);
        }
        return this;
    }

    public AdapterVerifier<Q, R, SQ, SR> assertSourceRequest(Q request, SQ expected) {
        SQ actual = toSourceRequest(request);
        if (!Objects.equals(expected, actual)) {
            throw new AssertionError("Source request mismatch for source '" + adapter.sourceId() + "'"
                    + System.lineSeparator() + "  expected: " + expected
                    + System.lineSeparator() + "  actual:   " + actual);
        }
        return this;
    }

    public AdapterVerifier<Q, R, SQ, SR> assertUnifiedResponse(Q request, SR sourceResponse, R expected) {
        R actual = toUnifiedResponse(request, sourceResponse);
        if (!Objects.equals(expected, actual)) {
            throw new AssertionError("Unified response mismatch for source '" + adapter.sourceId() + "'"
                    + System.lineSeparator() + "  expected: " + expected
                    + System.lineSeparator() + "  actual:   " + actual);
        }
        return this;
    }

    /** 校验适配器能容忍数据源返回 {@code null}（查无数据）而不抛出异常。 */
    public AdapterVerifier<Q, R, SQ, SR> assertHandlesNullResponse(Q request) {
        try {
            toUnifiedResponse(request, null);
        } catch (RuntimeException e) {
            throw new AssertionError("Adapter for source '" + adapter.sourceId()
                    + "' should handle a null source response, but threw " + e, e);
        }
        return this;
    }
}
