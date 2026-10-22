package com.unify.sdk.core;

import com.unify.sdk.api.Capability;
import com.unify.sdk.api.ErrorCode;
import com.unify.sdk.api.FailurePolicy;
import com.unify.sdk.api.InvokeOptions;
import com.unify.sdk.api.SourceStatus;
import com.unify.sdk.api.SourceTrace;
import com.unify.sdk.api.UnifiedClient;
import com.unify.sdk.api.UnifiedException;
import com.unify.sdk.api.UnifiedResult;

import java.lang.System.Logger.Level;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * 默认的统一客户端实现：路由 → 调度（顺序回退 / 并行合并）→ 转换 → 合并。
 */
final class DefaultUnifiedClient implements UnifiedClient {

    private static final System.Logger LOG = System.getLogger(DefaultUnifiedClient.class.getName());

    private final Map<String, CapabilityRuntime<?, ?>> runtimes;
    private final List<InvokeInterceptor> interceptors;
    private final Duration defaultTimeout;
    private final FailurePolicy defaultFailurePolicy;
    private final ExecutorService executor;
    private final boolean ownsExecutor;
    private final AtomicBoolean closed = new AtomicBoolean();

    DefaultUnifiedClient(Map<String, CapabilityRuntime<?, ?>> runtimes, List<InvokeInterceptor> interceptors,
                         Duration defaultTimeout, FailurePolicy defaultFailurePolicy,
                         ExecutorService executor, boolean ownsExecutor) {
        this.runtimes = Map.copyOf(runtimes);
        this.interceptors = List.copyOf(interceptors);
        this.defaultTimeout = defaultTimeout;
        this.defaultFailurePolicy = defaultFailurePolicy;
        this.executor = executor;
        this.ownsExecutor = ownsExecutor;
    }

    @Override
    public <Q, R> UnifiedResult<R> executeWithMeta(Capability<Q, R> capability, Q request, InvokeOptions options) {
        if (capability == null) {
            throw new UnifiedException(ErrorCode.INVALID_REQUEST, "capability must not be null");
        }
        if (closed.get()) {
            throw new UnifiedException(ErrorCode.INTERNAL_ERROR, "UnifiedClient has been closed");
        }
        CapabilityRuntime<Q, R> runtime = lookup(capability);
        if (!capability.requestType().isInstance(request)) {
            throw new UnifiedException(ErrorCode.INVALID_REQUEST,
                    "request must be a non-null instance of " + capability.requestType().getName(),
                    capability.name(), List.of(), null);
        }

        InvokeOptions opts = options == null ? InvokeOptions.defaults() : options;
        Duration timeout = opts.timeout().or(() -> runtime.config().timeout()).orElse(defaultTimeout);
        FailurePolicy policy = opts.failurePolicy().or(() -> runtime.config().failurePolicy()).orElse(defaultFailurePolicy);
        Instant deadline = Instant.now().plus(timeout);
        String traceId = opts.traceId().orElseGet(() -> UUID.randomUUID().toString());
        InvocationContext context = InvocationContext.create(capability.name(), traceId, opts, deadline);

        notifyInterceptors(i -> i.beforeInvoke(context, request));
        try {
            UnifiedResult<R> result = doExecute(runtime, request, context, deadline, policy);
            notifyInterceptors(i -> i.afterInvoke(context, result));
            return result;
        } catch (UnifiedException e) {
            notifyInterceptors(i -> i.onError(context, e));
            throw e;
        } catch (RuntimeException e) {
            UnifiedException wrapped = new UnifiedException(ErrorCode.INTERNAL_ERROR,
                    "Unexpected error: " + e, capability.name(), List.of(), e);
            notifyInterceptors(i -> i.onError(context, wrapped));
            throw wrapped;
        }
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true) || !ownsExecutor) {
            return;
        }
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    @SuppressWarnings("unchecked")
    private <Q, R> CapabilityRuntime<Q, R> lookup(Capability<Q, R> capability) {
        CapabilityRuntime<?, ?> runtime = runtimes.get(capability.name());
        if (runtime == null) {
            throw new UnifiedException(ErrorCode.CAPABILITY_NOT_FOUND,
                    "No adapter registered for capability '" + capability.name() + "'",
                    capability.name(), List.of(), null);
        }
        if (!runtime.capability().equals(capability)) {
            throw new UnifiedException(ErrorCode.INVALID_REQUEST,
                    "Capability definition mismatch: registered " + runtime.capability() + ", invoked with " + capability,
                    capability.name(), List.of(), null);
        }
        return (CapabilityRuntime<Q, R>) runtime;
    }

    private <Q, R> UnifiedResult<R> doExecute(CapabilityRuntime<Q, R> runtime, Q request, InvocationContext context,
                                              Instant deadline, FailurePolicy policy) {
        List<SourceBinding<Q, R>> selected = runtime.select(request, context);
        if (selected.isEmpty()) {
            throw new UnifiedException(ErrorCode.NO_AVAILABLE_SOURCE, "No source supports this request",
                    context.capability(), List.of(), null);
        }
        if (runtime.config().mode() == ExecutionMode.FAN_OUT_MERGE) {
            return fanOutMerge(runtime, selected, request, context, deadline, policy);
        }
        return firstAvailable(selected, request, context, deadline);
    }

    /** 顺序回退：逐个尝试，第一个成功即返回。 */
    private <Q, R> UnifiedResult<R> firstAvailable(List<SourceBinding<Q, R>> selected, Q request,
                                                   InvocationContext context, Instant deadline) {
        List<SourceTrace> traces = new ArrayList<>();
        SourceOutcome<R> lastFailure = null;
        for (SourceBinding<Q, R> binding : selected) {
            SourceOutcome<R> outcome = await(submit(binding, request, context, deadline), deadline);
            recordTrace(context, traces, outcome);
            if (outcome.isSuccess()) {
                boolean degraded = lastFailure != null;
                return new UnifiedResult<>(outcome.data(), context.traceId(), context.capability(), traces, degraded);
            }
            lastFailure = outcome;
        }
        throw allFailed(context, selected.size(), traces, lastFailure);
    }

    /** 并行合并：同时调用全部数据源，按失败策略处理后合并。 */
    private <Q, R> UnifiedResult<R> fanOutMerge(CapabilityRuntime<Q, R> runtime, List<SourceBinding<Q, R>> selected,
                                                Q request, InvocationContext context, Instant deadline,
                                                FailurePolicy policy) {
        List<PendingCall<R>> calls = new ArrayList<>(selected.size());
        for (SourceBinding<Q, R> binding : selected) {
            calls.add(submit(binding, request, context, deadline));
        }

        List<SourceTrace> traces = new ArrayList<>();
        List<SourcedResult<R>> successes = new ArrayList<>();
        SourceOutcome<R> lastFailure = null;
        for (int i = 0; i < calls.size(); i++) {
            SourceOutcome<R> outcome = await(calls.get(i), deadline);
            recordTrace(context, traces, outcome);
            if (outcome.isSuccess()) {
                successes.add(new SourcedResult<>(outcome.sourceId(), outcome.data()));
                continue;
            }
            lastFailure = outcome;
            if (policy == FailurePolicy.FAIL_FAST) {
                skipRemaining(calls, i + 1, context, traces);
                throw new UnifiedException(outcome.errorCode(),
                        "Source '" + outcome.sourceId() + "' failed under FAIL_FAST policy: " + outcome.message(),
                        context.capability(), traces, outcome.cause());
            }
        }
        if (successes.isEmpty()) {
            throw allFailed(context, selected.size(), traces, lastFailure);
        }

        ResultMerger<R> merger = runtime.config().merger().orElseThrow(() -> new UnifiedException(
                ErrorCode.INTERNAL_ERROR, "No ResultMerger configured", context.capability(), traces, null));
        R merged;
        try {
            merged = merger.merge(Collections.unmodifiableList(successes), context);
        } catch (RuntimeException e) {
            throw new UnifiedException(ErrorCode.MERGE_FAILED, "Failed to merge results: " + e.getMessage(),
                    context.capability(), traces, e);
        }
        Class<R> responseType = runtime.capability().responseType();
        if (merged != null && !responseType.isInstance(merged)) {
            throw new UnifiedException(ErrorCode.MERGE_FAILED,
                    "Merger returned " + merged.getClass().getName() + ", expected " + responseType.getName(),
                    context.capability(), traces, null);
        }
        boolean degraded = successes.size() < selected.size();
        return new UnifiedResult<>(merged, context.traceId(), context.capability(), traces, degraded);
    }

    private <Q, R> PendingCall<R> submit(SourceBinding<Q, R> binding, Q request, InvocationContext context,
                                         Instant deadline) {
        if (!Instant.now().isBefore(deadline)) {
            return PendingCall.done(SourceOutcome.<R>failure(binding.sourceId(), SourceStatus.SKIPPED,
                    ErrorCode.SOURCE_TIMEOUT, "Deadline exceeded before calling source", null, Duration.ZERO));
        }
        long start = System.nanoTime();
        Callable<R> task = () -> binding.invoke(request, context);
        try {
            return PendingCall.running(binding.sourceId(), executor.submit(task), start);
        } catch (RejectedExecutionException e) {
            return PendingCall.done(SourceOutcome.<R>failure(binding.sourceId(), SourceStatus.FAILED,
                    ErrorCode.INTERNAL_ERROR, "Executor rejected the call: " + e.getMessage(), e, elapsedSince(start)));
        }
    }

    private <R> SourceOutcome<R> await(PendingCall<R> call, Instant deadline) {
        if (call.completed() != null) {
            return call.completed();
        }
        String sourceId = call.sourceId();
        long remainingNanos = Math.max(0L, Duration.between(Instant.now(), deadline).toNanos());
        try {
            R data = call.future().get(remainingNanos, TimeUnit.NANOSECONDS);
            return SourceOutcome.success(sourceId, data, elapsedSince(call.startNanos()));
        } catch (TimeoutException e) {
            call.future().cancel(true);
            return SourceOutcome.failure(sourceId, SourceStatus.TIMEOUT, ErrorCode.SOURCE_TIMEOUT,
                    "Source did not respond before deadline", null, elapsedSince(call.startNanos()));
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof SourceInvocationException sie) {
                return SourceOutcome.failure(sourceId, SourceStatus.FAILED, sie.code(), sie.getMessage(),
                        sie.getCause(), elapsedSince(call.startNanos()));
            }
            return SourceOutcome.failure(sourceId, SourceStatus.FAILED, ErrorCode.SOURCE_FAILED,
                    "Unexpected error: " + cause, cause, elapsedSince(call.startNanos()));
        } catch (CancellationException e) {
            return SourceOutcome.failure(sourceId, SourceStatus.SKIPPED, ErrorCode.SOURCE_FAILED,
                    "Call was cancelled", e, elapsedSince(call.startNanos()));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            call.future().cancel(true);
            return SourceOutcome.failure(sourceId, SourceStatus.FAILED, ErrorCode.INTERNAL_ERROR,
                    "Interrupted while waiting for source", e, elapsedSince(call.startNanos()));
        }
    }

    private <R> void skipRemaining(List<PendingCall<R>> calls, int from, InvocationContext context,
                                   List<SourceTrace> traces) {
        for (int i = from; i < calls.size(); i++) {
            PendingCall<R> call = calls.get(i);
            if (call.completed() != null) {
                recordTrace(context, traces, call.completed());
                continue;
            }
            call.future().cancel(true);
            recordTrace(context, traces, SourceOutcome.<R>failure(call.sourceId(), SourceStatus.SKIPPED, null,
                    "Cancelled by FAIL_FAST policy", null, elapsedSince(call.startNanos())));
        }
    }

    private void recordTrace(InvocationContext context, List<SourceTrace> traces, SourceOutcome<?> outcome) {
        SourceTrace trace = outcome.toTrace();
        traces.add(trace);
        notifyInterceptors(i -> i.onSourceComplete(context, trace));
    }

    private static UnifiedException allFailed(InvocationContext context, int candidates, List<SourceTrace> traces,
                                              SourceOutcome<?> lastFailure) {
        if (candidates == 1 && lastFailure != null) {
            return new UnifiedException(lastFailure.errorCode(), lastFailure.message(),
                    context.capability(), traces, lastFailure.cause());
        }
        String last = lastFailure == null ? "n/a" : lastFailure.message();
        return new UnifiedException(ErrorCode.NO_AVAILABLE_SOURCE,
                "All " + candidates + " sources failed, last error: " + last,
                context.capability(), traces, lastFailure == null ? null : lastFailure.cause());
    }

    private void notifyInterceptors(Consumer<InvokeInterceptor> action) {
        for (InvokeInterceptor interceptor : interceptors) {
            try {
                action.accept(interceptor);
            } catch (RuntimeException e) {
                LOG.log(Level.WARNING, "Interceptor " + interceptor.getClass().getName() + " failed, ignored", e);
            }
        }
    }

    private static Duration elapsedSince(long startNanos) {
        return Duration.ofNanos(System.nanoTime() - startNanos);
    }

    /** 已提交（或因故未提交）的一次数据源调用。 */
    private record PendingCall<R>(String sourceId, Future<R> future, long startNanos, SourceOutcome<R> completed) {

        static <R> PendingCall<R> running(String sourceId, Future<R> future, long startNanos) {
            return new PendingCall<>(sourceId, future, startNanos, null);
        }

        static <R> PendingCall<R> done(SourceOutcome<R> outcome) {
            return new PendingCall<>(outcome.sourceId(), null, 0L, outcome);
        }
    }
}
