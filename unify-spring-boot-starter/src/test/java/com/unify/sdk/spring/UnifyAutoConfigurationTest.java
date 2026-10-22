package com.unify.sdk.spring;

import com.fasterxml.jackson.databind.node.TextNode;
import com.unify.sdk.ErrorCode;
import com.unify.sdk.JsonTransformer;
import com.unify.sdk.TransformException;
import com.unify.sdk.function.FunctionProvider;
import com.unify.sdk.function.TransformFunction;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class UnifyAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(UnifyAutoConfiguration.class));

    @Test
    void registersTransformerWithMappingsFromDefaultLocation() {
        runner.run(context -> {
            assertThat(context).hasSingleBean(JsonTransformer.class);
            JsonTransformer transformer = context.getBean(JsonTransformer.class);

            assertThat(transformer.mappingNames()).containsExactly("greeting");
            assertThat(transformer.mapping("greeting").description()).isEqualTo("测试用映射");
            assertThat(transformer.transformByName("greeting", "{\"name\":\"Unify\"}"))
                    .isEqualTo("{\"message\":\"Hello, Unify\"}");
        });
    }

    @Test
    void registersFunctionBeansByBeanNameAndFunctionProviders() {
        runner.withUserConfiguration(FunctionConfig.class)
                .withPropertyValues("unify.mapping-locations="
                        + UnifyProperties.DEFAULT_MAPPING_LOCATION + ",classpath:unify-extra/*.json")
                .run(context -> {
                    JsonTransformer transformer = context.getBean(JsonTransformer.class);

                    assertThat(transformer.functionNames()).contains("tag", "twice", "concat");
                    assertThat(transformer.mappingNames()).containsExactly("greeting", "tagged");
                    assertThat(transformer.transformByName("tagged", "{\"id\":7}")).isEqualTo("{\"id\":\"#7\"}");
                    assertThat(transformer.transform("{\"v\":\"ab\"}", "{\"mapping\":\"=twice($.v)\"}"))
                            .isEqualTo("\"abab\"");
                });
    }

    @Test
    void appliesCustomizersInOrder() {
        runner.withUserConfiguration(CustomizerConfig.class).run(context -> {
            JsonTransformer transformer = context.getBean(JsonTransformer.class);

            assertThat(transformer.mappingNames()).containsExactly("greeting", "inline", "later");
            assertThat(transformer.transformByName("inline", "{}")).isEqualTo("{\"value\":\"picked\"}");
        });
    }

    @Test
    void canBeDisabled() {
        runner.withPropertyValues("unify.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(JsonTransformer.class));
    }

    @Test
    void backsOffWhenTransformerIsDefined() {
        runner.withUserConfiguration(CustomTransformerConfig.class).run(context -> {
            assertThat(context).hasSingleBean(JsonTransformer.class);
            assertThat(context.getBean(JsonTransformer.class).mappingNames()).isEmpty();
        });
    }

    @Test
    void locationsWithoutFilesRegisterNothing() {
        runner.withPropertyValues("unify.mapping-locations=classpath*:nothing-here/**/*.json")
                .run(context -> assertThat(context.getBean(JsonTransformer.class).mappingNames()).isEmpty());
    }

    @Test
    void invalidMappingFileFailsStartup() {
        runner.withPropertyValues("unify.mapping-locations=classpath:unify-broken/*.json").run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).rootCause()
                    .isInstanceOf(TransformException.class)
                    .hasMessageContaining("映射 broken 位置 /mapping/id")
                    .hasMessageContaining("broken.json");
            TransformException failure = (TransformException) rootCause(context.getStartupFailure());
            assertThat(failure.errorCode()).isEqualTo(ErrorCode.INVALID_DSL);
        });
    }

    @Test
    void invalidFunctionBeanNameFailsStartup() {
        runner.withUserConfiguration(InvalidFunctionNameConfig.class).run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).rootCause()
                    .hasMessageContaining("函数名无效：mask-phone");
            assertThat(context.getStartupFailure()).hasStackTraceContaining("无法注册函数 Bean mask-phone");
        });
    }

    @Test
    void invalidCacheSizeFailsStartup() {
        runner.withPropertyValues("unify.cache-size=-1").run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).rootCause()
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("cacheSize");
        });
    }

    private static Throwable rootCause(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current;
    }

    @Configuration(proxyBeanMethods = false)
    static class FunctionConfig {

        @Bean
        TransformFunction tag() {
            return TransformFunction.of(1, 1, args -> TextNode.valueOf("#" + args.text(0)));
        }

        @Bean
        FunctionProvider extraFunctions() {
            return () -> Map.of("twice", TransformFunction.of(1, 1, args -> TextNode.valueOf(args.text(0).repeat(2))));
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class CustomizerConfig {

        @Bean
        @Order(2)
        TransformerCustomizer later() {
            return builder -> builder.mapping("{\"name\":\"later\",\"mapping\":{}}");
        }

        @Bean
        @Order(1)
        TransformerCustomizer earlier() {
            return builder -> builder
                    .function("pick", TransformFunction.of(0, 0, args -> TextNode.valueOf("picked")))
                    .mapping("{\"name\":\"inline\",\"mapping\":{\"value\":\"=pick()\"}}", "customizer");
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class CustomTransformerConfig {

        @Bean
        JsonTransformer customTransformer() {
            return JsonTransformer.create();
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class InvalidFunctionNameConfig {

        @Bean("mask-phone")
        TransformFunction maskPhone() {
            return TransformFunction.of(1, 1, args -> args.get(0));
        }
    }
}
