package com.kekwy.unifabric.fabric.scheduling;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 调度与退避参数（论文 3.4.2 / 3.4.3）。
 */
@ConfigurationProperties(prefix = "unifabric.fabric.scheduling")
public class SchedulingProperties {

    /**
     * Score = alpha * f_LR + (1-alpha) * f_BA，默认 0.5。
     */
    private double alpha = 0.5;

    private long backoffInitSeconds = 1;

    private long backoffMaxSeconds = 30;

    private int maxRetries = 10;

    private long requestTtlSeconds = 300;

    private int deployTimeoutSeconds = 120;

    public double getAlpha() {
        return alpha;
    }

    public void setAlpha(double alpha) {
        this.alpha = alpha;
    }

    public long getBackoffInitSeconds() {
        return backoffInitSeconds;
    }

    public void setBackoffInitSeconds(long backoffInitSeconds) {
        this.backoffInitSeconds = backoffInitSeconds;
    }

    public long getBackoffMaxSeconds() {
        return backoffMaxSeconds;
    }

    public void setBackoffMaxSeconds(long backoffMaxSeconds) {
        this.backoffMaxSeconds = backoffMaxSeconds;
    }

    public int getMaxRetries() {
        return maxRetries;
    }

    public void setMaxRetries(int maxRetries) {
        this.maxRetries = maxRetries;
    }

    public long getRequestTtlSeconds() {
        return requestTtlSeconds;
    }

    public void setRequestTtlSeconds(long requestTtlSeconds) {
        this.requestTtlSeconds = requestTtlSeconds;
    }

    public int getDeployTimeoutSeconds() {
        return deployTimeoutSeconds;
    }

    public void setDeployTimeoutSeconds(int deployTimeoutSeconds) {
        this.deployTimeoutSeconds = deployTimeoutSeconds;
    }

    /**
     * 论文式 (\ref{eq:backoff})：T_backoff(n) = min(T_init * 2^(n-1), T_max)，n 从 1 起为首次退避。
     */
    public long backoffMillisForAttempt(int schedulingFailures) {
        if (schedulingFailures <= 0) {
            return 0;
        }
        double seconds = Math.min(
                (double) backoffInitSeconds * Math.pow(2, schedulingFailures - 1),
                (double) backoffMaxSeconds);
        return Math.max(1, (long) (seconds * 1000));
    }
}
