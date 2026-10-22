package com.unify.sdk.testkit;

import com.unify.sdk.spi.DataSource;
import com.unify.sdk.spi.DataSourceException;
import com.unify.sdk.spi.SourceContext;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/**
 * 可编程的桩数据源，用于在没有真实内部接口时测试适配器和整体调用链路。
 *
 * <pre>{@code
 * StubDataSource<LegacyReq, LegacyResp> legacy = StubDataSource.of("legacy-crm", req -> new LegacyResp(...));
 * StubDataSource<LegacyReq, LegacyResp> broken = StubDataSource.failing("legacy-crm", new DataSourceException("down"));
 * }</pre>
 */
public final class StubDataSource<SQ, SR> implements DataSource<SQ, SR> {

    private final String sourceId;
    private volatile Function<SQ, SR> handler;
    private volatile Duration delay = Duration.ZERO;
    private final List<SQ> requests = Collections.synchronizedList(new ArrayList<>());
    private final List<SourceContext> contexts = Collections.synchronizedList(new ArrayList<>());

    private StubDataSource(String sourceId, Function<SQ, SR> handler) {
        this.sourceId = Objects.requireNonNull(sourceId, "sourceId");
        this.handler = Objects.requireNonNull(handler, "handler");
    }

    /** 按请求动态生成响应。 */
    public static <SQ, SR> StubDataSource<SQ, SR> of(String sourceId, Function<SQ, SR> handler) {
        return new StubDataSource<>(sourceId, handler);
    }

    /** 固定返回同一个响应（可为 {@code null}，模拟查无数据）。 */
    public static <SQ, SR> StubDataSource<SQ, SR> returning(String sourceId, SR response) {
        return new StubDataSource<>(sourceId, request -> response);
    }

    /** 每次调用都抛出指定异常。 */
    public static <SQ, SR> StubDataSource<SQ, SR> failing(String sourceId, RuntimeException error) {
        Objects.requireNonNull(error, "error");
        return new StubDataSource<>(sourceId, request -> {
            throw error;
        });
    }

    /** 替换响应逻辑，便于在同一测试中切换场景。 */
    public StubDataSource<SQ, SR> respondWith(Function<SQ, SR> handler) {
        this.handler = Objects.requireNonNull(handler, "handler");
        return this;
    }

    /** 每次调用前等待指定时长，用于模拟慢接口与超时。 */
    public StubDataSource<SQ, SR> withDelay(Duration delay) {
        this.delay = Objects.requireNonNull(delay, "delay");
        return this;
    }

    @Override
    public String sourceId() {
        return sourceId;
    }

    @Override
    public SR fetch(SQ request, SourceContext context) {
        requests.add(request);
        contexts.add(context);
        Duration currentDelay = delay;
        if (!currentDelay.isZero()) {
            try {
                Thread.sleep(currentDelay.toMillis());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new DataSourceException("Interrupted while simulating delay", e);
            }
        }
        return handler.apply(request);
    }

    public int invocationCount() {
        return requests.size();
    }

    /** 收到的全部内部请求，按调用顺序排列。 */
    public List<SQ> requests() {
        synchronized (requests) {
            return new ArrayList<>(requests);
        }
    }

    public SQ lastRequest() {
        synchronized (requests) {
            if (requests.isEmpty()) {
                throw new IllegalStateException("DataSource '" + sourceId + "' has not been called");
            }
            return requests.get(requests.size() - 1);
        }
    }

    public SourceContext lastContext() {
        synchronized (contexts) {
            if (contexts.isEmpty()) {
                throw new IllegalStateException("DataSource '" + sourceId + "' has not been called");
            }
            return contexts.get(contexts.size() - 1);
        }
    }

    public void reset() {
        requests.clear();
        contexts.clear();
    }
}
