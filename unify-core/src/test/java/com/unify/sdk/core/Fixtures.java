package com.unify.sdk.core;

import com.unify.sdk.api.Capability;
import com.unify.sdk.spi.DataSource;
import com.unify.sdk.spi.DataSourceException;
import com.unify.sdk.spi.SourceContext;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * 核心模块测试用的统一模型、数据源与适配器。
 */
final class Fixtures {

    record Req(String id) {
    }

    record Resp(String id, String value) {
    }

    static final Capability<Req, Resp> CAP = Capability.of("test.query", Req.class, Resp.class);

    private Fixtures() {
    }

    /** 内部接口：请求为字符串 ID，响应为字符串值。 */
    static final class TestSource implements DataSource<String, String> {

        private final String sourceId;
        private final Function<String, String> handler;
        private final Duration delay;
        final AtomicInteger calls = new AtomicInteger();

        TestSource(String sourceId, Function<String, String> handler, Duration delay) {
            this.sourceId = sourceId;
            this.handler = handler;
            this.delay = delay;
        }

        static TestSource ok(String sourceId) {
            return new TestSource(sourceId, id -> sourceId + ":" + id, Duration.ZERO);
        }

        static TestSource failing(String sourceId) {
            return new TestSource(sourceId, id -> {
                throw new DataSourceException("boom", "E500", true, null);
            }, Duration.ZERO);
        }

        static TestSource slow(String sourceId, Duration delay) {
            return new TestSource(sourceId, id -> sourceId + ":" + id, delay);
        }

        @Override
        public String sourceId() {
            return sourceId;
        }

        @Override
        public String fetch(String request, SourceContext context) {
            calls.incrementAndGet();
            if (!delay.isZero()) {
                try {
                    Thread.sleep(delay.toMillis());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new DataSourceException("interrupted", e);
                }
            }
            return handler.apply(request);
        }
    }

    static final class TestAdapter implements SourceAdapter<Req, Resp, String, String> {

        private final String sourceId;
        private final Predicate<Req> supports;
        private final int order;

        TestAdapter(String sourceId, Predicate<Req> supports, int order) {
            this.sourceId = sourceId;
            this.supports = supports;
            this.order = order;
        }

        static TestAdapter of(String sourceId) {
            return new TestAdapter(sourceId, req -> true, 0);
        }

        static TestAdapter prefix(String sourceId, String idPrefix) {
            return new TestAdapter(sourceId, req -> req.id().startsWith(idPrefix), 0);
        }

        @Override
        public String sourceId() {
            return sourceId;
        }

        @Override
        public Capability<Req, Resp> capability() {
            return CAP;
        }

        @Override
        public boolean supports(Req request, InvocationContext context) {
            return supports.test(request);
        }

        @Override
        public int order() {
            return order;
        }

        @Override
        public String toSourceRequest(Req request, InvocationContext context) {
            return request.id();
        }

        @Override
        public Resp toUnifiedResponse(String response, Req request, InvocationContext context) {
            if (response == null) {
                return null;
            }
            if (response.contains("bad")) {
                throw new IllegalArgumentException("cannot parse " + response);
            }
            return new Resp(request.id(), response);
        }
    }
}
