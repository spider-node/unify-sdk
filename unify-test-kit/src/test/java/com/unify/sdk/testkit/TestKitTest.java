package com.unify.sdk.testkit;

import com.unify.sdk.api.Capability;
import com.unify.sdk.api.UnifiedClient;
import com.unify.sdk.core.InvocationContext;
import com.unify.sdk.core.SourceAdapter;
import com.unify.sdk.core.UnifiedClients;
import com.unify.sdk.spi.DataSourceException;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TestKitTest {

    record Query(String id) {
    }

    record Result(String text) {
    }

    static final Capability<Query, Result> CAP = Capability.of("kit.query", Query.class, Result.class);

    static final class UpperAdapter implements SourceAdapter<Query, Result, String, String> {

        @Override
        public String sourceId() {
            return "upper";
        }

        @Override
        public Capability<Query, Result> capability() {
            return CAP;
        }

        @Override
        public boolean supports(Query request, InvocationContext context) {
            return request.id().startsWith("U");
        }

        @Override
        public String toSourceRequest(Query request, InvocationContext context) {
            return request.id().toLowerCase();
        }

        @Override
        public Result toUnifiedResponse(String response, Query request, InvocationContext context) {
            return response == null ? null : new Result(response.toUpperCase());
        }
    }

    @Test
    void verifierChecksBothDirections() {
        AdapterVerifier.of(new UpperAdapter())
                .assertSupports(new Query("U1"))
                .assertNotSupports(new Query("X1"))
                .assertSourceRequest(new Query("U1"), "u1")
                .assertUnifiedResponse(new Query("U1"), "abc", new Result("ABC"))
                .assertHandlesNullResponse(new Query("U1"));
    }

    @Test
    void verifierReportsMismatch() {
        AdapterVerifier<Query, Result, String, String> verifier = AdapterVerifier.of(new UpperAdapter());
        assertThrows(AssertionError.class, () -> verifier.assertSourceRequest(new Query("U1"), "U1"));
    }

    @Test
    void stubRecordsRequestsAndSupportsScenarios() {
        StubDataSource<String, String> stub = StubDataSource.of("upper", request -> "value-" + request);
        try (UnifiedClient client = UnifiedClients.builder().bind(stub, new UpperAdapter()).build()) {
            assertEquals(new Result("VALUE-U1"), client.execute(CAP, new Query("U1")));
            assertEquals("u1", stub.lastRequest());
            assertEquals(1, stub.invocationCount());
            assertEquals("kit.query", stub.lastContext().capability());

            stub.respondWith(request -> {
                throw new DataSourceException("down");
            });
            assertThrows(RuntimeException.class, () -> client.execute(CAP, new Query("U2")));
            assertEquals(2, stub.invocationCount());
        }
    }

    @Test
    void stubCanSimulateSlowSource() {
        StubDataSource<String, String> stub = StubDataSource.<String, String>returning("upper", "x")
                .withDelay(Duration.ofMillis(50));
        try (UnifiedClient client = UnifiedClients.builder().bind(stub, new UpperAdapter()).build()) {
            assertEquals(new Result("X"), client.execute(CAP, new Query("U1")));
        }
    }
}
