package com.kekwy.unifabric.fabric.provider;

import com.kekwy.unifabric.proto.provider.InstanceStatus;

import java.time.Instant;

/**
 * 管理节点侧跟踪的单个计算实例容器状态。
 */
public class InstanceRecord {

    private final String instanceId;
    private final String providerId;
    private volatile InstanceStatus status;
    private final Instant createdAt;
    private volatile Instant lastUpdated;
    private volatile String message;

    /** 论文 3.5.2：Provider 上报的容器网络可达地址 */
    private volatile String endpointHost;
    private volatile int endpointPort;
    private volatile boolean hasLocalEndpoint;

    public InstanceRecord(String instanceId, String providerId, InstanceStatus initialStatus) {
        this.instanceId = instanceId;
        this.providerId = providerId;
        this.status = initialStatus != null ? initialStatus : InstanceStatus.INSTANCE_STATUS_UNSPECIFIED;
        this.createdAt = Instant.now();
        this.lastUpdated = this.createdAt;
    }

    public String getInstanceId() {
        return instanceId;
    }

    public String getProviderId() {
        return providerId;
    }

    public InstanceStatus getStatus() {
        return status;
    }

    public void setStatus(InstanceStatus status) {
        this.status = status != null ? status : InstanceStatus.INSTANCE_STATUS_UNSPECIFIED;
        this.lastUpdated = Instant.now();
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getLastUpdated() {
        return lastUpdated;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
        this.lastUpdated = Instant.now();
    }

    public void setLocalEndpoint(String host, int port) {
        this.endpointHost = host;
        this.endpointPort = port;
        this.hasLocalEndpoint = host != null && !host.isBlank() && port > 0;
        this.lastUpdated = Instant.now();
    }

    public String getEndpointHost() {
        return endpointHost;
    }

    public int getEndpointPort() {
        return endpointPort;
    }

    public boolean hasLocalEndpoint() {
        return hasLocalEndpoint;
    }
}
