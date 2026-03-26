package com.kekwy.unifabric.actor;

import com.kekwy.unifabric.proto.common.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Combine 节点多路输入缓冲：按 executionId 汇聚两路 input_port 数据，
 * 两路都到齐时返回 ReadyPair 供立即执行；超时后以缺失端口为 null 通过回调触发执行。
 */
public final class CombineBuffer {

    private static final Logger log = LoggerFactory.getLogger(CombineBuffer.class);

    private static final int COMBINE_INPUT_PORTS = 2;

    /** 同一 executionId 下两路输入到齐时的可执行结果。left/right 可为 null 表示该路未到达（超时场景）。 */
    public record ReadyPair(String executionId, Value left, Value right) {}

    private static final class PendingCombine {
        private final long createdAt;
        private Value left;
        private Value right;
        private boolean leftReceived;
        private boolean rightReceived;

        PendingCombine(long createdAt) {
            this.createdAt = createdAt;
        }

        synchronized void set(int inputPort, Value value) {
            if (inputPort == 0) {
                left = value;
                leftReceived = true;
            } else {
                right = value;
                rightReceived = true;
            }
        }

        synchronized boolean hasBoth() {
            return leftReceived && rightReceived;
        }

        synchronized ReadyPair toReadyPair(String executionId) {
            return new ReadyPair(
                    executionId,
                    leftReceived ? left : null,
                    rightReceived ? right : null
            );
        }

        long getCreatedAt() {
            return createdAt;
        }
    }

    private final ConcurrentHashMap<String, PendingCombine> pending = new ConcurrentHashMap<>();
    private final long timeoutMs;
    private final ReadyConsumer onTimeoutReady;
    private final ScheduledExecutorService scheduler;
    private final AtomicBoolean shutdown = new AtomicBoolean(false);

    /**
     * 超时时对「未到齐」的 executionId 调用的回调（缺失端口为 null）。
     */
    @FunctionalInterface
    public interface ReadyConsumer {
        void accept(ReadyPair pair);
    }

    /**
     * @param timeoutMs     同一 executionId 两路输入最大等待时间（毫秒），超时后以缺失为 null 触发 onTimeoutReady
     * @param onTimeoutReady 超时时的回调，在 scheduler 线程中调用
     */
    public CombineBuffer(long timeoutMs, ReadyConsumer onTimeoutReady) {
        this.timeoutMs = timeoutMs;
        this.onTimeoutReady = onTimeoutReady;
        this.scheduler = new ScheduledThreadPoolExecutor(1, r -> {
            Thread t = new Thread(r, "combine-buffer-timeout");
            t.setDaemon(true);
            return t;
        });
        scheduler.scheduleWithFixedDelay(this::evictTimedOut, timeoutMs / 2, timeoutMs / 2, TimeUnit.MILLISECONDS);
    }

    /**
     * 投递一路输入。若该 executionId 下两路均已到达，则从缓存移除并返回 ReadyPair；否则返回 null。
     */
    public ReadyPair offer(String executionId, int inputPort, Value value) {
        if (executionId == null || executionId.isBlank()) {
            log.warn("CombineBuffer.offer 收到空 executionId，忽略");
            return null;
        }
        if (inputPort < 0 || inputPort >= COMBINE_INPUT_PORTS) {
            log.warn("CombineBuffer.offer 非法 inputPort={}, executionId={}", inputPort, executionId);
            return null;
        }
        boolean valueEmpty = value.getKindCase() == Value.KindCase.KIND_NOT_SET;
        log.debug("[flow] COMBINE offer executionId={} inputPort={} valueEmpty={}", executionId, inputPort, valueEmpty);

        long now = System.currentTimeMillis();
        PendingCombine p = pending.computeIfAbsent(executionId, k -> new PendingCombine(now));
        synchronized (p) {
            p.set(inputPort, value);
            if (p.hasBoth()) {
                pending.remove(executionId);
                ReadyPair pair = p.toReadyPair(executionId);
                log.info("[flow] COMBINE 两路到齐 executionId={} leftEmpty={} rightEmpty={}",
                        executionId,
                        pair.left() == null || pair.left().getKindCase() == Value.KindCase.KIND_NOT_SET,
                        pair.right() == null || pair.right().getKindCase() == Value.KindCase.KIND_NOT_SET);
                return pair;
            }
        }
        return null;
    }

    private void evictTimedOut() {
        if (shutdown.get()) return;
        long now = System.currentTimeMillis();
        long deadline = now - timeoutMs;
        pending.forEach((executionId, p) -> {
            if (p.getCreatedAt() < deadline) {
                PendingCombine removed = pending.remove(executionId);
                if (removed != null) {
                    ReadyPair pair = removed.toReadyPair(executionId);
                    log.info("[flow] COMBINE 缓冲超时 executionId={} 以缺失端口为 null 触发执行", executionId);
                    try {
                        onTimeoutReady.accept(pair);
                    } catch (Throwable t) {
                        log.warn("Combine 超时回调执行失败: executionId={}", executionId, t);
                    }
                }
            }
        });
    }

    /**
     * 停止定时清理任务，不再触发超时回调。
     */
    public void shutdown() {
        if (shutdown.compareAndSet(false, true)) {
            scheduler.shutdownNow();
        }
    }
}
