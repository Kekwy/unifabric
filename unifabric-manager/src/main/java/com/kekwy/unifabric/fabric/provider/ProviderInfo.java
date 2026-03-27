package com.kekwy.unifabric.fabric.provider;

import com.kekwy.unifabric.common.util.ResourceSpecUtil;
import com.kekwy.unifabric.proto.common.ResourceCapacity;
import com.kekwy.unifabric.proto.common.ResourceSpec;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 记录一个已注册 Provider 的元数据与实时状态。
 * <p>
 * 生命周期状态与论文 3.2.2 节一致：Registering / Connecting / Online / Degraded / Offline / Reconnecting。
 */
public class ProviderInfo {

    /**
     * Provider 生命周期状态（论文 3.2.2）。
     */
    public enum Status {
        /** 适配器正在注册，管理侧在 register 返回前通常不可见 */
        REGISTERING,
        /** 已分配 id，三条流式通道尚未全部就绪 */
        CONNECTING,
        /** 三通道就绪且心跳正常 */
        ONLINE,
        /** 控制通道正常但部署或信令通道断开，暂停新部署 */
        DEGRADED,
        /** 控制通道心跳超时 */
        OFFLINE,
        /** 曾离线后控制通道恢复心跳，通道可能仍在补齐 */
        RECONNECTING
    }

    private final String providerId;
    private final String name;
    private final String description;
    private final String providerType;
    private final String zone;

    /** 注册时静态标签；运行期可由心跳快照覆盖，见 {@link #updateTagsFromHeartbeat(List)} */
    private volatile List<String> tags;

    private volatile ResourceCapacity resourceCapacity;
    private volatile Instant lastHeartbeat;
    private volatile Status status;

    /**
     * 乐观记账：调度器已承诺但尚未确认的资源需求（论文 3.4.1），key 为调度请求 ID。
     */
    private final ConcurrentHashMap<String, ResourceSpec> pendingDeductionsByRequestId = new ConcurrentHashMap<>();

    public ProviderInfo(String providerId, String name, String description,
                        String providerType, String zone, List<String> tags) {
        this.providerId = providerId;
        this.name = name;
        this.description = description;
        this.providerType = providerType;
        this.zone = zone;
        this.tags = tags != null ? List.copyOf(tags) : List.of();
        this.lastHeartbeat = Instant.now();
        this.status = Status.CONNECTING;
    }

    public String getProviderId() {
        return providerId;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public String getProviderType() {
        return providerType;
    }

    public String getZone() {
        return zone;
    }

    public List<String> getTags() {
        return tags;
    }

    /**
     * 由心跳携带的标签快照更新（论文 3.2.3）。
     */
    public void updateTagsFromHeartbeat(List<String> newTags) {
        if (newTags == null) {
            return;
        }
        this.tags = List.copyOf(newTags);
    }

    public ResourceCapacity getResourceCapacity() {
        return resourceCapacity;
    }

    public void setResourceCapacity(ResourceCapacity resourceCapacity) {
        this.resourceCapacity = resourceCapacity;
    }

    public Instant getLastHeartbeat() {
        return lastHeartbeat;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public void touchHeartbeat() {
        this.lastHeartbeat = Instant.now();
    }

    /** 资源上报到达时更新容量并刷新活跃时间 */
    public void touchResourceReport(ResourceCapacity capacity) {
        if (capacity != null) {
            this.resourceCapacity = capacity;
        }
        this.lastHeartbeat = Instant.now();
    }

    // ======================== 乐观记账（论文 3.4.1） ========================

    /**
     * 登记乐观扣减；同一 requestId 重复登记返回 false。
     */
    public boolean addPendingDeduction(String requestId, ResourceSpec demand) {
        if (requestId == null || requestId.isBlank() || demand == null) {
            return false;
        }
        return pendingDeductionsByRequestId.putIfAbsent(requestId, demand) == null;
    }

    /**
     * 移除乐观扣减（部署失败回滚或流程结束）。
     */
    public ResourceSpec removePendingDeduction(String requestId) {
        if (requestId == null) {
            return null;
        }
        return pendingDeductionsByRequestId.remove(requestId);
    }

    /**
     * 当前所有待确认承诺的资源向量之和。
     */
    public ResourceSpec aggregatePendingDemand() {
        return ResourceSpecUtil.sum(pendingDeductionsByRequestId.values());
    }

    public int getPendingDeductionCount() {
        return pendingDeductionsByRequestId.size();
    }

    /**
     * 在心跳上报的 {@link ResourceCapacity} 基础上，将 {@code available} 扣除全部 pending，
     * 供调度 Filter 使用（论文式 (3) 与 3.4.3 节 Avail 定义）。
     */
    public ResourceCapacity getEffectiveResourceCapacity() {
        ResourceCapacity cap = resourceCapacity;
        if (cap == null || ResourceCapacity.getDefaultInstance().equals(cap)) {
            return cap;
        }
        ResourceSpec pending = aggregatePendingDemand();
        if (ResourceSpecUtil.isEffectivelyZero(pending)) {
            return cap;
        }
        ResourceSpec baseAvail = cap.hasAvailable() ? cap.getAvailable() : ResourceSpecUtil.zero();
        ResourceSpec newAvail = ResourceSpecUtil.subtractNonNegative(baseAvail, pending);
        return cap.toBuilder().setAvailable(newAvail).build();
    }

    @Override
    public String toString() {
        return "ProviderInfo{id=" + providerId + ", name=" + name +
                ", type=" + providerType + ", zone=" + zone + ", status=" + status + "}";
    }
}
