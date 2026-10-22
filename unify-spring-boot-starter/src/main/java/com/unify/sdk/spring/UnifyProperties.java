package com.unify.sdk.spring;

import com.unify.sdk.api.FailurePolicy;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Unify SDK 配置项，前缀 {@code unify}。
 *
 * <pre>
 * unify:
 *   enabled: true
 *   default-timeout: 3s
 *   default-failure-policy: BEST_EFFORT
 *   logging-enabled: true
 * </pre>
 */
@ConfigurationProperties(prefix = "unify")
public class UnifyProperties {

    /** 是否启用自动装配。 */
    private boolean enabled = true;

    /** 全局默认超时，可被能力配置与单次调用参数覆盖。 */
    private Duration defaultTimeout = Duration.ofSeconds(3);

    /** 并行合并模式下的全局默认失败策略。 */
    private FailurePolicy defaultFailurePolicy = FailurePolicy.BEST_EFFORT;

    /** 是否注册内置的调用日志拦截器。 */
    private boolean loggingEnabled = false;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Duration getDefaultTimeout() {
        return defaultTimeout;
    }

    public void setDefaultTimeout(Duration defaultTimeout) {
        this.defaultTimeout = defaultTimeout;
    }

    public FailurePolicy getDefaultFailurePolicy() {
        return defaultFailurePolicy;
    }

    public void setDefaultFailurePolicy(FailurePolicy defaultFailurePolicy) {
        this.defaultFailurePolicy = defaultFailurePolicy;
    }

    public boolean isLoggingEnabled() {
        return loggingEnabled;
    }

    public void setLoggingEnabled(boolean loggingEnabled) {
        this.loggingEnabled = loggingEnabled;
    }
}
