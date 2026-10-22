package com.unify.sdk.core;

import com.unify.sdk.api.ErrorCode;
import com.unify.sdk.spi.DataSource;
import com.unify.sdk.spi.DataSourceException;

/**
 * 适配器与数据源的绑定，完成"请求转换 → 调用数据源 → 响应转换"的完整链路。
 */
final class SourceBinding<Q, R> {

    private final SourceAdapter<Q, R, Object, Object> adapter;
    private final DataSource<Object, Object> dataSource;
    private final Class<R> responseType;

    SourceBinding(SourceAdapter<Q, R, Object, Object> adapter, DataSource<Object, Object> dataSource) {
        this.adapter = adapter;
        this.dataSource = dataSource;
        this.responseType = adapter.capability().responseType();
    }

    String sourceId() {
        return adapter.sourceId();
    }

    int order() {
        return adapter.order();
    }

    SourceAdapter<Q, R, Object, Object> adapter() {
        return adapter;
    }

    R invoke(Q request, InvocationContext context) {
        Object sourceRequest;
        try {
            sourceRequest = adapter.toSourceRequest(request, context);
        } catch (RuntimeException e) {
            throw new SourceInvocationException(ErrorCode.CONVERT_FAILED,
                    "Failed to convert request for source '" + sourceId() + "': " + describe(e), e);
        }

        Object sourceResponse;
        try {
            sourceResponse = dataSource.fetch(sourceRequest, context.toSourceContext());
        } catch (DataSourceException e) {
            String code = e.errorCode() == null ? "" : " [" + e.errorCode() + "]";
            throw new SourceInvocationException(ErrorCode.SOURCE_FAILED,
                    "Source '" + sourceId() + "' failed" + code + ": " + e.getMessage(), e);
        } catch (RuntimeException e) {
            throw new SourceInvocationException(ErrorCode.SOURCE_FAILED,
                    "Source '" + sourceId() + "' failed: " + describe(e), e);
        }

        R response;
        try {
            response = adapter.toUnifiedResponse(sourceResponse, request, context);
        } catch (RuntimeException e) {
            throw new SourceInvocationException(ErrorCode.CONVERT_FAILED,
                    "Failed to convert response from source '" + sourceId() + "': " + describe(e), e);
        }
        if (response != null && !responseType.isInstance(response)) {
            throw new SourceInvocationException(ErrorCode.CONVERT_FAILED,
                    "Adapter for source '" + sourceId() + "' returned " + response.getClass().getName()
                            + ", expected " + responseType.getName(), null);
        }
        return response;
    }

    private static String describe(Throwable e) {
        return e.getMessage() == null ? e.getClass().getSimpleName() : e.getClass().getSimpleName() + ": " + e.getMessage();
    }
}
