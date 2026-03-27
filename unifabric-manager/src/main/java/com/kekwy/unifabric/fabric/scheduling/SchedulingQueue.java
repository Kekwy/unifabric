package com.kekwy.unifabric.fabric.scheduling;

import com.kekwy.unifabric.fabric.provider.InstanceLifecycleListener;
import com.kekwy.unifabric.fabric.provider.InstanceRegistry;
import com.kekwy.unifabric.proto.provider.InstanceStatus;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * 就绪队列 + 退避重试 + 事件唤醒（论文 3.4.2）。
 */
@Component
public class SchedulingQueue implements InstanceLifecycleListener {

    private static final Logger log = LoggerFactory.getLogger(SchedulingQueue.class);

    private final DefaultSchedulingService schedulingService;
    private final SchedulingProperties properties;
    private final InstanceRegistry instanceRegistry;

    private final BlockingQueue<WorkItem> ready = new LinkedBlockingQueue<>();
    private final ConcurrentHashMap<String, WorkItem> inBackoff = new ConcurrentHashMap<>();

    private final ScheduledExecutorService timer = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "scheduling-backoff");
        t.setDaemon(true);
        return t;
    });

    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "scheduling-queue-worker");
        t.setDaemon(true);
        return t;
    });

    public SchedulingQueue(DefaultSchedulingService schedulingService,
                           SchedulingProperties properties,
                           InstanceRegistry instanceRegistry) {
        this.schedulingService = schedulingService;
        this.properties = properties;
        this.instanceRegistry = instanceRegistry;
    }

    @PostConstruct
    void start() {
        instanceRegistry.addListener(this);
        worker.submit(this::runLoop);
    }

    @PreDestroy
    void stop() {
        worker.shutdownNow();
        timer.shutdownNow();
    }

    /**
     * 将请求放入就绪队列，异步完成 {@link CompletableFuture}。
     */
    public CompletableFuture<SchedulingResult> enqueue(DeployRequest request) {
        CompletableFuture<SchedulingResult> future = new CompletableFuture<>();
        ready.offer(new WorkItem(request, future));
        return future;
    }

    @EventListener
    public void onTopologyChanged(SchedulingTopologyChangedEvent event) {
        wakeBackoff();
    }

    @Override
    public void onInstanceStatusChanged(String instanceId, String providerId,
                                        InstanceStatus previous, InstanceStatus current, String message) {
        if (current == InstanceStatus.STOPPED
                || current == InstanceStatus.REMOVED
                || current == InstanceStatus.FAILED) {
            wakeBackoff();
        }
    }

    /**
     * 取消退避定时并尽快重新入队（论文 3.4.2 事件驱动触发）。
     */
    public void wakeBackoff() {
        List<WorkItem> snapshot = new ArrayList<>(inBackoff.values());
        for (WorkItem w : snapshot) {
            ScheduledFuture<?> sf = w.backoffFuture;
            if (sf != null) {
                sf.cancel(false);
            }
            if (inBackoff.remove(w.queueItemId, w) && !w.future.isDone()) {
                boolean offered = ready.offer(w);
                if (!offered) {
                    log.warn("就绪队列无法接纳唤醒项，重新进入退避: queueItemId={}", w.queueItemId);
                    scheduleBackoff(w);
                }
            }
        }
    }

    private void runLoop() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                WorkItem w = ready.take();
                processOne(w);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                log.warn("调度队列处理异常: {}", e.getMessage(), e);
            }
        }
    }

    private void processOne(WorkItem w) {
        if (w.future.isDone()) {
            return;
        }
        if (ttlExpired(w.request)) {
            inBackoff.remove(w.queueItemId);
            w.future.complete(SchedulingResult.failure("调度请求已超过 TTL"));
            return;
        }
        if (w.schedulingFailures >= properties.getMaxRetries()) {
            inBackoff.remove(w.queueItemId);
            w.future.complete(SchedulingResult.failure("超过最大调度重试次数"));
            return;
        }
        SchedulingResult result = schedulingService.scheduleBlocking(w.request);
        if (result.isSuccess()) {
            inBackoff.remove(w.queueItemId);
            w.future.complete(result);
            return;
        }
        w.schedulingFailures++;
        if (w.schedulingFailures >= properties.getMaxRetries()) {
            inBackoff.remove(w.queueItemId);
            w.future.complete(result);
            return;
        }
        scheduleBackoff(w);
    }

    private void scheduleBackoff(WorkItem w) {
        long delayMs = properties.backoffMillisForAttempt(w.schedulingFailures);
        inBackoff.put(w.queueItemId, w);
        w.backoffFuture = timer.schedule(() -> {
            if (inBackoff.remove(w.queueItemId, w) && !w.future.isDone()) {
                if (ttlExpired(w.request)) {
                    w.future.complete(SchedulingResult.failure("调度请求已超过 TTL"));
                    return;
                }
                ready.offer(w);
            }
        }, delayMs, TimeUnit.MILLISECONDS);
        log.debug("请求进入退避 queueItemId={} failures={} delayMs={}", w.queueItemId, w.schedulingFailures, delayMs);
    }

    private boolean ttlExpired(DeployRequest request) {
        Instant deadline = request.getCreatedAt().plusSeconds(properties.getRequestTtlSeconds());
        return Instant.now().isAfter(deadline);
    }

    private static final class WorkItem {
        final String queueItemId = UUID.randomUUID().toString();
        final DeployRequest request;
        final CompletableFuture<SchedulingResult> future;
        int schedulingFailures;
        volatile ScheduledFuture<?> backoffFuture;

        WorkItem(DeployRequest request, CompletableFuture<SchedulingResult> future) {
            this.request = request;
            this.future = future;
        }
    }
}
