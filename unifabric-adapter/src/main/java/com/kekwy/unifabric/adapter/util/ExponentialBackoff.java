package com.kekwy.unifabric.adapter.util;

import java.util.concurrent.ThreadLocalRandom;

/**
 * 论文 3.2.2：指数退避 + 抖动，与 gRPC 连接退避思路一致。
 */
public class ExponentialBackoff {

    private final long initialIntervalMs;
    private final double multiplier;
    private final long maxIntervalMs;
    private final double jitterRatio;
    private int attempt;

    public ExponentialBackoff() {
        this(1000L, 1.6, 120_000L, 0.2);
    }

    public ExponentialBackoff(long initialIntervalMs, double multiplier, long maxIntervalMs, double jitterRatio) {
        this.initialIntervalMs = Math.max(1L, initialIntervalMs);
        this.multiplier = multiplier > 1.0 ? multiplier : 1.6;
        this.maxIntervalMs = Math.max(initialIntervalMs, maxIntervalMs);
        this.jitterRatio = Math.min(0.5, Math.max(0, jitterRatio));
    }

    /** 下一次等待毫秒数（调用后递增尝试次数） */
    public long nextDelayMs() {
        double base = initialIntervalMs * Math.pow(multiplier, attempt);
        attempt++;
        long capped = (long) Math.min(maxIntervalMs, base);
        if (jitterRatio <= 0) {
            return capped;
        }
        double j = 1.0 + (ThreadLocalRandom.current().nextDouble() * 2 - 1) * jitterRatio;
        return Math.max(1L, (long) (capped * j));
    }

    public void reset() {
        attempt = 0;
    }
}
