package com.kekwy.unifabric.fabric.provider;

import com.kekwy.unifabric.proto.provider.InstanceStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 批量部署跟踪上下文（论文 3.2.4）：全部 Running 成功，任一 Failed 则失败。
 */
@Component
public class DeploymentTracker {

    private static final Logger log = LoggerFactory.getLogger(DeploymentTracker.class);

    public record BatchDeploymentResult(boolean success, String taskId, String message) {}

    private static final class Context {
        final String taskId;
        final Map<String, InstanceStatus> statuses = new ConcurrentHashMap<>();
        final CompletableFuture<BatchDeploymentResult> future;

        Context(String taskId, List<String> instanceIds, CompletableFuture<BatchDeploymentResult> future) {
            this.taskId = taskId;
            this.future = future;
            for (String id : instanceIds) {
                if (id != null && !id.isBlank()) {
                    statuses.put(id, InstanceStatus.PENDING);
                }
            }
        }
    }

    private final ConcurrentHashMap<String, Context> contexts = new ConcurrentHashMap<>();

    /**
     * 创建批量部署任务，返回异步结果。
     */
    public CompletableFuture<BatchDeploymentResult> createBatchDeployment(List<String> instanceIds) {
        String taskId = UUID.randomUUID().toString();
        CompletableFuture<BatchDeploymentResult> future = new CompletableFuture<>();
        Context ctx = new Context(taskId, instanceIds, future);
        contexts.put(taskId, ctx);
        log.info("批量部署任务已创建: taskId={}, instances={}", taskId, ctx.statuses.size());
        return future;
    }

    /**
     * 由 {@link DefaultInstanceRegistry} 在实例状态变更时调用。
     */
    public void onInstanceStatusUpdate(String instanceId, InstanceStatus status) {
        if (instanceId == null || status == null) {
            return;
        }
        for (Context ctx : contexts.values()) {
            if (!ctx.statuses.containsKey(instanceId)) {
                continue;
            }
            ctx.statuses.put(instanceId, status);
            if (status == InstanceStatus.FAILED) {
                complete(ctx, false, "实例失败: " + instanceId);
                continue;
            }
            boolean allRunning = ctx.statuses.values().stream()
                    .allMatch(s -> s == InstanceStatus.RUNNING);
            if (allRunning && !ctx.statuses.isEmpty()) {
                complete(ctx, true, "全部实例 Running");
            }
        }
    }

    private void complete(Context ctx, boolean success, String message) {
        contexts.remove(ctx.taskId, ctx);
        if (!ctx.future.isDone()) {
            ctx.future.complete(new BatchDeploymentResult(success, ctx.taskId, message));
            log.info("批量部署任务结束: taskId={}, success={}, {}", ctx.taskId, success, message);
        }
    }
}
