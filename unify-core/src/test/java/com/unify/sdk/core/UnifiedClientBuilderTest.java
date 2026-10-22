package com.unify.sdk.core;

import com.unify.sdk.core.Fixtures.TestAdapter;
import com.unify.sdk.core.Fixtures.TestSource;
import org.junit.jupiter.api.Test;

import static com.unify.sdk.core.Fixtures.CAP;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UnifiedClientBuilderTest {

    @Test
    void adapterWithoutDataSourceFailsAtBuildTime() {
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> UnifiedClients.builder().adapter(TestAdapter.of("missing")).build());
        assertTrue(e.getMessage().contains("missing"));
    }

    @Test
    void duplicateDataSourceIdFails() {
        assertThrows(IllegalStateException.class, () -> UnifiedClients.builder()
                .dataSource(TestSource.ok("a"))
                .dataSource(TestSource.ok("a"))
                .build());
    }

    @Test
    void sameDataSourceInstanceMayBeRegisteredTwice() {
        TestSource shared = TestSource.ok("a");
        UnifiedClients.builder()
                .dataSource(shared)
                .bind(shared, TestAdapter.of("a"))
                .build()
                .close();
    }

    @Test
    void duplicateAdapterForSameCapabilityAndSourceFails() {
        assertThrows(IllegalStateException.class, () -> UnifiedClients.builder()
                .dataSource(TestSource.ok("a"))
                .adapter(TestAdapter.of("a"))
                .adapter(TestAdapter.of("a"))
                .build());
    }

    @Test
    void bindRejectsMismatchedSourceIds() {
        assertThrows(IllegalArgumentException.class,
                () -> UnifiedClients.builder().bind(TestSource.ok("a"), TestAdapter.of("b")));
    }

    @Test
    void configWithoutAdapterFails() {
        assertThrows(IllegalStateException.class, () -> UnifiedClients.builder()
                .capability(CapabilityConfig.defaults(CAP))
                .build());
    }

    @Test
    void fanOutWithoutMergerFails() {
        assertThrows(IllegalStateException.class,
                () -> CapabilityConfig.builder(CAP).mode(ExecutionMode.FAN_OUT_MERGE).build());
    }
}
