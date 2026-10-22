package com.unify.sdk.core.interceptor;

import com.unify.sdk.api.SourceTrace;
import com.unify.sdk.api.UnifiedException;
import com.unify.sdk.api.UnifiedResult;
import com.unify.sdk.core.InvocationContext;
import com.unify.sdk.core.InvokeInterceptor;

import java.lang.System.Logger;
import java.lang.System.Logger.Level;

/**
 * 基于 {@link System.Logger} 的调用日志拦截器，不引入任何日志框架依赖
 * （SLF4J、Log4j2 等均可通过各自的桥接接管 System.Logger）。
 * <p>
 * 成功调用记录为 DEBUG，数据源失败、降级、调用异常记录为 WARNING。
 */
public class LoggingInterceptor implements InvokeInterceptor {

    private static final Logger LOG = System.getLogger("com.unify.sdk.invoke");

    @Override
    public void onSourceComplete(InvocationContext context, SourceTrace trace) {
        if (trace.isSuccess()) {
            if (LOG.isLoggable(Level.DEBUG)) {
                LOG.log(Level.DEBUG, prefix(context) + "source=" + trace.sourceId()
                        + " status=SUCCESS elapsed=" + millis(trace) + "ms");
            }
            return;
        }
        LOG.log(Level.WARNING, prefix(context) + "source=" + trace.sourceId() + " status=" + trace.status()
                + " code=" + trace.errorCode() + " elapsed=" + millis(trace) + "ms message=" + trace.errorMessage());
    }

    @Override
    public void afterInvoke(InvocationContext context, UnifiedResult<?> result) {
        if (result.degraded()) {
            LOG.log(Level.WARNING, prefix(context) + "completed with DEGRADED result, successful sources="
                    + result.successfulSources());
        } else if (LOG.isLoggable(Level.DEBUG)) {
            LOG.log(Level.DEBUG, prefix(context) + "completed, sources=" + result.successfulSources());
        }
    }

    @Override
    public void onError(InvocationContext context, UnifiedException error) {
        LOG.log(Level.WARNING, prefix(context) + "failed: " + error.getMessage());
    }

    private static String prefix(InvocationContext context) {
        return "[unify] capability=" + context.capability() + " traceId=" + context.traceId() + " ";
    }

    private static long millis(SourceTrace trace) {
        return trace.elapsed() == null ? -1 : trace.elapsed().toMillis();
    }
}
