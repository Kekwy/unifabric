package com.kekwy.unifabric.fabric.discovery;

import com.kekwy.unifabric.proto.common.ResourceCapacity;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * 控制节点资源视图中的单个节点信息。
 * <p>
 * 远程条目在版本更高时可通过 {@link #updateFromGossip} 完整覆盖（论文 3.3.2 版本化合并）。
 */
public class PeerNode {

    private final String nodeId;
    private volatile String nodeName;
    private volatile String address;
    private volatile String domainId;
    private volatile List<String> tags;

    private volatile ResourceCapacity capacity;
    private volatile long version;
    private volatile int gossipCount;
    private volatile long lastSeenMs;

    public PeerNode(String nodeId,
                    String nodeName,
                    String address,
                    String domainId,
                    List<String> tags,
                    ResourceCapacity capacity,
                    long version,
                    int gossipCount,
                    long lastSeenMs) {
        this.nodeId = Objects.requireNonNull(nodeId, "nodeId must not be null");
        this.nodeName = nodeName != null ? nodeName : nodeId;
        this.address = address != null ? address : "";
        this.domainId = domainId != null ? domainId : "";
        this.tags = tags != null ? List.copyOf(tags) : List.of();
        this.capacity = capacity != null ? capacity : ResourceCapacity.getDefaultInstance();
        this.version = version;
        this.gossipCount = gossipCount;
        this.lastSeenMs = lastSeenMs > 0 ? lastSeenMs : Instant.now().toEpochMilli();
    }

    public String getNodeId() {
        return nodeId;
    }

    public String getNodeName() {
        return nodeName;
    }

    public String getAddress() {
        return address;
    }

    public String getDomainId() {
        return domainId;
    }

    public List<String> getTags() {
        return tags;
    }

    public ResourceCapacity getCapacity() {
        return capacity;
    }

    public long getVersion() {
        return version;
    }

    public int getGossipCount() {
        return gossipCount;
    }

    public long getLastSeenMs() {
        return lastSeenMs;
    }

    /**
     * 本域心跳聚合路径：更新容量与版本（仅当 version 更高时提升版本号）。
     */
    public synchronized void updateCapacity(ResourceCapacity capacity, long version) {
        if (capacity != null) {
            this.capacity = capacity;
        }
        if (version > this.version) {
            this.version = version;
        }
        this.lastSeenMs = Instant.now().toEpochMilli();
    }

    /**
     * Gossip 合并：新版本严格大于本地时完整覆盖条目（论文算法 2）。
     */
    public synchronized void updateFromGossip(String nodeName,
                                                String address,
                                                String domainId,
                                                List<String> tags,
                                                ResourceCapacity capacity,
                                                long version,
                                                int gossipCount,
                                                long lastSeenMs) {
        if (version <= this.version) {
            return;
        }
        this.nodeName = nodeName != null && !nodeName.isEmpty() ? nodeName : this.nodeName;
        this.address = address != null ? address : "";
        this.domainId = domainId != null ? domainId : "";
        this.tags = tags != null ? List.copyOf(tags) : List.of();
        if (capacity != null) {
            this.capacity = capacity;
        }
        this.version = version;
        this.gossipCount = gossipCount;
        this.lastSeenMs = lastSeenMs > 0 ? lastSeenMs : Instant.now().toEpochMilli();
    }

    /**
     * 版本相同且对端观测时间更新时，仅刷新 lastSeen 与 gossipCount（论文算法 2）。
     */
    public synchronized void refreshIfNewerObservation(long incomingLastSeenMs, int incomingGossipCount) {
        if (incomingLastSeenMs > this.lastSeenMs) {
            this.lastSeenMs = incomingLastSeenMs;
            this.gossipCount = incomingGossipCount;
        }
    }

    /**
     * 出站 Gossip：递增传播计数（论文 3.3.2）。
     */
    public synchronized int incrementGossipCountForSend() {
        this.gossipCount = this.gossipCount + 1;
        return this.gossipCount;
    }

    @Override
    public String toString() {
        return "PeerNode{" +
                "nodeId='" + nodeId + '\'' +
                ", address='" + address + '\'' +
                ", domainId='" + domainId + '\'' +
                ", version=" + version +
                ", lastSeenMs=" + lastSeenMs +
                '}';
    }
}
