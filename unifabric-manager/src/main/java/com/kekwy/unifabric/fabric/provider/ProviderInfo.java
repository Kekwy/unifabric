package com.kekwy.unifabric.fabric.provider;

import com.kekwy.unifabric.proto.provider.ResourceCapacity;

import java.time.Instant;
import java.util.List;

/**
 * 记录一个已注册 Provider 的元数据与实时状态。
 */
public class ProviderInfo {

    public enum Status {
        ONLINE, OFFLINE
    }

    private final String providerId;
    private final String name;
    private final String description;
    private final String providerType;
    private final String zone;
    private final List<String> tags;

    private volatile ResourceCapacity capacity;
    private volatile ResourceCapacity usage;
    private volatile Instant lastHeartbeat;
    private volatile Status status;

    public ProviderInfo(String providerId, String name, String description,
                        String providerType, String zone, List<String> tags) {
        this.providerId = providerId;
        this.name = name;
        this.description = description;
        this.providerType = providerType;
        this.zone = zone;
        this.tags = tags != null ? List.copyOf(tags) : List.of();
        this.lastHeartbeat = Instant.now();
        this.status = Status.ONLINE;
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

    public ResourceCapacity getCapacity() {
        return capacity;
    }

    public void setCapacity(ResourceCapacity capacity) {
        this.capacity = capacity;
    }

    public ResourceCapacity getUsage() {
        return usage;
    }

    public void updateUsage(ResourceCapacity usage) {
        this.usage = usage;
        this.lastHeartbeat = Instant.now();
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

    @Override
    public String toString() {
        return "ProviderInfo{id=" + providerId + ", name=" + name +
                ", type=" + providerType + ", zone=" + zone + ", status=" + status + "}";
    }
}
