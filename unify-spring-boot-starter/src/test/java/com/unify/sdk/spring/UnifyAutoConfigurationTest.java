package com.unify.sdk.spring;

import com.unify.sdk.api.Capability;
import com.unify.sdk.api.ErrorCode;
import com.unify.sdk.api.InvokeOptions;
import com.unify.sdk.api.UnifiedClient;
import com.unify.sdk.api.UnifiedException;
import com.unify.sdk.api.UnifiedResult;
import com.unify.sdk.core.CapabilityConfig;
import com.unify.sdk.core.InvocationContext;
import com.unify.sdk.core.SourceAdapter;
import com.unify.sdk.spi.DataSource;
import com.unify.sdk.spi.SourceContext;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UnifyAutoConfigurationTest {

    record Query(String id) {
    }

    record Result(String text) {
    }

    static final Capability<Query, Result> CAP = Capability.of("spring.query", Query.class, Result.class);

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(UnifyAutoConfiguration.class));

    static final class EchoSource implements DataSource<String, String> {

        private final Duration delay;

        EchoSource(Duration delay) {
            this.delay = delay;
        }

        @Override
        public String sourceId() {
            return "echo";
        }

        @Override
        public String fetch(String request, SourceContext context) {
            if (!delay.isZero()) {
                try {
                    Thread.sleep(delay.toMillis());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            return "echo:" + request;
        }
    }

    static final class EchoAdapter implements SourceAdapter<Query, Result, String, String> {

        @Override
        public String sourceId() {
            return "echo";
        }

        @Override
        public Capability<Query, Result> capability() {
            return CAP;
        }

        @Override
        public String toSourceRequest(Query request, InvocationContext context) {
            return request.id();
        }

        @Override
        public Result toUnifiedResponse(String response, Query request, InvocationContext context) {
            return response == null ? null : new Result(response);
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class EchoConfig {

        @Bean
        DataSource<String, String> echoSource() {
            return new EchoSource(Duration.ZERO);
        }

        @Bean
        SourceAdapter<Query, Result, String, String> echoAdapter() {
            return new EchoAdapter();
        }

        @Bean
        CapabilityConfig<Query, Result> echoCapabilityConfig() {
            return CapabilityConfig.builder(CAP).timeout(Duration.ofSeconds(1)).build();
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class SlowEchoConfig {

        @Bean
        DataSource<String, String> echoSource() {
            return new EchoSource(Duration.ofMillis(1500));
        }

        @Bean
        SourceAdapter<Query, Result, String, String> echoAdapter() {
            return new EchoAdapter();
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class AdapterOnlyConfig {

        @Bean
        SourceAdapter<Query, Result, String, String> echoAdapter() {
            return new EchoAdapter();
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class CustomClientConfig {

        static final UnifiedClient CUSTOM = new UnifiedClient() {
            @Override
            public <Q, R> UnifiedResult<R> executeWithMeta(Capability<Q, R> capability, Q request,
                                                           InvokeOptions options) {
                throw new UnsupportedOperationException();
            }

            @Override
            public void close() {
            }
        };

        @Bean
        UnifiedClient customClient() {
            return CUSTOM;
        }
    }

    @Test
    void registersClientFromBeans() {
        runner.withUserConfiguration(EchoConfig.class).run(context -> {
            UnifiedClient client = context.getBean(UnifiedClient.class);
            assertEquals(new Result("echo:a"), client.execute(CAP, new Query("a")));
        });
    }

    @Test
    void bindsDefaultTimeoutFromProperties() {
        runner.withUserConfiguration(SlowEchoConfig.class)
                .withPropertyValues("unify.default-timeout=100ms")
                .run(context -> {
                    UnifiedClient client = context.getBean(UnifiedClient.class);
                    UnifiedException error = assertThrows(UnifiedException.class,
                            () -> client.execute(CAP, new Query("a")));
                    assertEquals(ErrorCode.SOURCE_TIMEOUT, error.code());
                });
    }

    @Test
    void appliesCustomizers() {
        runner.withUserConfiguration(AdapterOnlyConfig.class)
                .withBean(UnifiedClientCustomizer.class,
                        () -> builder -> builder.dataSource(new EchoSource(Duration.ZERO)))
                .run(context -> {
                    UnifiedClient client = context.getBean(UnifiedClient.class);
                    assertEquals(new Result("echo:b"), client.execute(CAP, new Query("b")));
                });
    }

    @Test
    void failsFastOnMisconfiguration() {
        runner.withUserConfiguration(AdapterOnlyConfig.class).run(context -> {
            assertNotNull(context.getStartupFailure());
            assertTrue(rootCause(context.getStartupFailure()) instanceof IllegalStateException);
        });
    }

    @Test
    void canBeDisabled() {
        runner.withUserConfiguration(EchoConfig.class)
                .withPropertyValues("unify.enabled=false")
                .run(context -> assertTrue(context.getBeansOfType(UnifiedClient.class).isEmpty()));
    }

    @Test
    void backsOffWhenUserDefinesClient() {
        runner.withUserConfiguration(CustomClientConfig.class).run(context ->
                assertSame(CustomClientConfig.CUSTOM, context.getBean(UnifiedClient.class)));
    }

    private static Throwable rootCause(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current;
    }
}
