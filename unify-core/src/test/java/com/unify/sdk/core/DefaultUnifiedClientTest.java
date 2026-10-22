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
import com.unify.sdk.core.Fixtures.Req;
import com.unify.sdk.core.Fixtures.Resp;
import com.unify.sdk.core.Fixtures.TestAdapter;
import com.unify.sdk.core.Fixtures.TestSource;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static com.unify.sdk.core.Fixtures.CAP;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefaultUnifiedClientTest {

    @Test
    void routesToTheSourceThatSupportsTheRequest() {
        TestSource legacy = TestSource.ok("legacy");
        TestSource modern = TestSource.ok("modern");
        try (UnifiedClient client = UnifiedClients.builder()
                .bind(legacy, TestAdapter.prefix("legacy", "L"))
                .bind(modern, TestAdapter.prefix("modern", "M"))
                .build()) {

            assertEquals(new Resp("L1", "legacy:L1"), client.execute(CAP, new Req("L1")));
            assertEquals(new Resp("M1", "modern:M1"), client.execute(CAP, new Req("M1")));
            assertEquals(1, legacy.calls.get());
            assertEquals(1, modern.calls.get());
        }
    }

    @Test
    void fallsBackToNextSourceWhenFirstFails() {
        try (UnifiedClient client = UnifiedClients.builder()
                .bind(TestSource.failing("primary"), TestAdapter.of("primary"))
                .bind(TestSource.ok("backup"), TestAdapter.of("backup"))
                .build()) {

            UnifiedResult<Resp> result = client.executeWithMeta(CAP, new Req("X"));

            assertEquals("backup:X", result.data().value());
            assertTrue(result.degraded());
            assertEquals(List.of("backup"), result.successfulSources());
            assertEquals(2, result.traces().size());
            SourceTrace failed = result.traces().get(0);
            assertEquals(SourceStatus.FAILED, failed.status());
            assertEquals(ErrorCode.SOURCE_FAILED, failed.errorCode());
            assertTrue(failed.errorMessage().contains("E500"));
        }
    }

    @Test
    void adapterOrderDecidesTryOrder() {
        TestSource first = TestSource.ok("first");
        TestSource second = TestSource.ok("second");
        try (UnifiedClient client = UnifiedClients.builder()
                .bind(second, new TestAdapter("second", req -> true, 10))
                .bind(first, new TestAdapter("first", req -> true, 1))
                .build()) {

            assertEquals("first:X", client.execute(CAP, new Req("X")).value());
            assertEquals(0, second.calls.get());
        }
    }

    @Test
    void preferredSourceIsTriedFirst() {
        try (UnifiedClient client = UnifiedClients.builder()
                .bind(TestSource.ok("a"), TestAdapter.of("a"))
                .bind(TestSource.ok("b"), TestAdapter.of("b"))
                .build()) {

            InvokeOptions options = InvokeOptions.builder().preferredSource("b").build();
            assertEquals("b:X", client.execute(CAP, new Req("X"), options).value());
        }
    }

    @Test
    void customRouterCanExcludeSources() {
        try (UnifiedClient client = UnifiedClients.builder()
                .bind(TestSource.ok("a"), TestAdapter.of("a"))
                .bind(TestSource.ok("b"), TestAdapter.of("b"))
                .capability(CapabilityConfig.builder(CAP).router(Router.exclude("a")).build())
                .build()) {

            assertEquals("b:X", client.execute(CAP, new Req("X")).value());
        }
    }

    @Test
    void singleSourceFailureKeepsItsErrorCode() {
        try (UnifiedClient client = UnifiedClients.builder()
                .bind(TestSource.failing("only"), TestAdapter.of("only"))
                .build()) {

            UnifiedException e = assertThrows(UnifiedException.class, () -> client.execute(CAP, new Req("X")));
            assertEquals(ErrorCode.SOURCE_FAILED, e.code());
            assertEquals("test.query", e.capability());
            assertEquals(1, e.traces().size());
        }
    }

    @Test
    void allSourcesFailingReportsNoAvailableSource() {
        try (UnifiedClient client = UnifiedClients.builder()
                .bind(TestSource.failing("a"), TestAdapter.of("a"))
                .bind(TestSource.failing("b"), TestAdapter.of("b"))
                .build()) {

            UnifiedException e = assertThrows(UnifiedException.class, () -> client.execute(CAP, new Req("X")));
            assertEquals(ErrorCode.NO_AVAILABLE_SOURCE, e.code());
            assertEquals(2, e.traces().size());
        }
    }

    @Test
    void noSupportingSourceReportsNoAvailableSource() {
        try (UnifiedClient client = UnifiedClients.builder()
                .bind(TestSource.ok("legacy"), TestAdapter.prefix("legacy", "L"))
                .build()) {

            UnifiedException e = assertThrows(UnifiedException.class, () -> client.execute(CAP, new Req("Z1")));
            assertEquals(ErrorCode.NO_AVAILABLE_SOURCE, e.code());
        }
    }

    @Test
    void slowSourceTimesOut() {
        try (UnifiedClient client = UnifiedClients.builder()
                .bind(TestSource.slow("slow", Duration.ofSeconds(2)), TestAdapter.of("slow"))
                .build()) {

            InvokeOptions options = InvokeOptions.builder().timeout(Duration.ofMillis(100)).build();
            long start = System.nanoTime();
            UnifiedException e = assertThrows(UnifiedException.class,
                    () -> client.execute(CAP, new Req("X"), options));
            long elapsedMillis = Duration.ofNanos(System.nanoTime() - start).toMillis();

            assertEquals(ErrorCode.SOURCE_TIMEOUT, e.code());
            assertEquals(SourceStatus.TIMEOUT, e.traces().get(0).status());
            assertTrue(elapsedMillis < 1500, "should not wait for the slow source, took " + elapsedMillis + "ms");
        }
    }

    @Test
    void conversionFailureIsReportedAsConvertFailed() {
        try (UnifiedClient client = UnifiedClients.builder()
                .bind(new TestSource("bad", id -> "bad-data", Duration.ZERO), TestAdapter.of("bad"))
                .build()) {

            UnifiedException e = assertThrows(UnifiedException.class, () -> client.execute(CAP, new Req("X")));
            assertEquals(ErrorCode.CONVERT_FAILED, e.code());
        }
    }

    @Test
    void nullSourceResponseIsAValidEmptyResult() {
        try (UnifiedClient client = UnifiedClients.builder()
                .bind(new TestSource("empty", id -> null, Duration.ZERO), TestAdapter.of("empty"))
                .build()) {

            UnifiedResult<Resp> result = client.executeWithMeta(CAP, new Req("X"));
            assertNull(result.data());
            assertFalse(result.degraded());
        }
    }

    @Test
    void fanOutMergeCombinesAllSources() {
        try (UnifiedClient client = UnifiedClients.builder()
                .bind(TestSource.slow("a", Duration.ofMillis(200)), TestAdapter.of("a"))
                .bind(TestSource.slow("b", Duration.ofMillis(200)), TestAdapter.of("b"))
                .capability(CapabilityConfig.builder(CAP)
                        .fanOutMerge(Mergers.reduce((x, y) -> new Resp(x.id(), x.value() + "|" + y.value())))
                        .build())
                .build()) {

            long start = System.nanoTime();
            UnifiedResult<Resp> result = client.executeWithMeta(CAP, new Req("X"));
            long elapsedMillis = Duration.ofNanos(System.nanoTime() - start).toMillis();

            assertEquals("a:X|b:X", result.data().value());
            assertFalse(result.degraded());
            assertEquals(List.of("a", "b"), result.successfulSources());
            assertTrue(elapsedMillis < 380, "sources should be called in parallel, took " + elapsedMillis + "ms");
        }
    }

    @Test
    void fanOutBestEffortReturnsPartialResult() {
        try (UnifiedClient client = UnifiedClients.builder()
                .bind(TestSource.ok("a"), TestAdapter.of("a"))
                .bind(TestSource.failing("b"), TestAdapter.of("b"))
                .capability(CapabilityConfig.builder(CAP).fanOutMerge(Mergers.firstNonNull()).build())
                .defaultFailurePolicy(FailurePolicy.BEST_EFFORT)
                .build()) {

            UnifiedResult<Resp> result = client.executeWithMeta(CAP, new Req("X"));
            assertEquals("a:X", result.data().value());
            assertTrue(result.degraded());
        }
    }

    @Test
    void fanOutFailFastThrowsOnAnyFailure() {
        try (UnifiedClient client = UnifiedClients.builder()
                .bind(TestSource.failing("a"), TestAdapter.of("a"))
                .bind(TestSource.ok("b"), TestAdapter.of("b"))
                .capability(CapabilityConfig.builder(CAP)
                        .fanOutMerge(Mergers.firstNonNull())
                        .failurePolicy(FailurePolicy.FAIL_FAST)
                        .build())
                .build()) {

            UnifiedException e = assertThrows(UnifiedException.class, () -> client.execute(CAP, new Req("X")));
            assertEquals(ErrorCode.SOURCE_FAILED, e.code());
            assertEquals(2, e.traces().size());
        }
    }

    @Test
    void mergerFailureIsReportedAsMergeFailed() {
        try (UnifiedClient client = UnifiedClients.builder()
                .bind(TestSource.ok("a"), TestAdapter.of("a"))
                .capability(CapabilityConfig.builder(CAP)
                        .fanOutMerge((results, ctx) -> {
                            throw new IllegalStateException("merge bug");
                        })
                        .build())
                .build()) {

            UnifiedException e = assertThrows(UnifiedException.class, () -> client.execute(CAP, new Req("X")));
            assertEquals(ErrorCode.MERGE_FAILED, e.code());
        }
    }

    @Test
    void interceptorsReceiveLifecycleEvents() {
        List<String> events = new CopyOnWriteArrayList<>();
        InvokeInterceptor recorder = new InvokeInterceptor() {
            @Override
            public void beforeInvoke(InvocationContext context, Object request) {
                events.add("before");
            }

            @Override
            public void onSourceComplete(InvocationContext context, SourceTrace trace) {
                events.add("source:" + trace.sourceId() + ":" + trace.status());
            }

            @Override
            public void afterInvoke(InvocationContext context, UnifiedResult<?> result) {
                events.add("after");
            }
        };
        InvokeInterceptor broken = new InvokeInterceptor() {
            @Override
            public void beforeInvoke(InvocationContext context, Object request) {
                throw new IllegalStateException("must be ignored");
            }
        };
        try (UnifiedClient client = UnifiedClients.builder()
                .bind(TestSource.ok("a"), TestAdapter.of("a"))
                .interceptor(broken)
                .interceptor(recorder)
                .build()) {

            client.execute(CAP, new Req("X"));
            assertEquals(List.of("before", "source:a:SUCCESS", "after"), events);
        }
    }

    @Test
    void attributesAndTraceIdArePassedToDataSource() {
        List<String> seen = new CopyOnWriteArrayList<>();
        com.unify.sdk.spi.DataSource<String, String> source = new com.unify.sdk.spi.DataSource<>() {
            @Override
            public String sourceId() {
                return "ctx";
            }

            @Override
            public String fetch(String request, com.unify.sdk.spi.SourceContext context) {
                seen.add(context.traceId());
                seen.add(String.valueOf(context.attribute("tenant").orElse("none")));
                seen.add(context.capability());
                return "ok";
            }
        };
        try (UnifiedClient client = UnifiedClients.builder().bind(source, TestAdapter.of("ctx")).build()) {
            InvokeOptions options = InvokeOptions.builder().traceId("trace-1").attribute("tenant", "t1").build();
            UnifiedResult<Resp> result = client.executeWithMeta(CAP, new Req("X"), options);

            assertEquals("trace-1", result.traceId());
            assertEquals(List.of("trace-1", "t1", "test.query"), seen);
        }
    }

    @Test
    void invalidInvocationsAreRejected() {
        try (UnifiedClient client = UnifiedClients.builder()
                .bind(TestSource.ok("a"), TestAdapter.of("a"))
                .build()) {

            UnifiedException nullRequest = assertThrows(UnifiedException.class, () -> client.execute(CAP, null));
            assertEquals(ErrorCode.INVALID_REQUEST, nullRequest.code());

            Capability<Req, String> unknown = Capability.of("unknown", Req.class, String.class);
            UnifiedException notFound = assertThrows(UnifiedException.class,
                    () -> client.execute(unknown, new Req("X")));
            assertEquals(ErrorCode.CAPABILITY_NOT_FOUND, notFound.code());

            Capability<Req, String> mismatch = Capability.of("test.query", Req.class, String.class);
            UnifiedException conflict = assertThrows(UnifiedException.class,
                    () -> client.execute(mismatch, new Req("X")));
            assertEquals(ErrorCode.INVALID_REQUEST, conflict.code());
        }
    }

    @Test
    void closedClientRejectsCalls() {
        UnifiedClient client = UnifiedClients.builder().bind(TestSource.ok("a"), TestAdapter.of("a")).build();
        client.close();
        assertThrows(UnifiedException.class, () -> client.execute(CAP, new Req("X")));
    }
}
