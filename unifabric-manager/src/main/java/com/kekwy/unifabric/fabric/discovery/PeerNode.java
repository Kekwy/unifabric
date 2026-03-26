package com.kekwy.unifabric.fabric.discovery;

import com.kekwy.unifabric.proto.resource.ResourceCapacity;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * 控制节点资源视图中的单个节点信息。
 * <p>
 * 这是对 proto {@code PeerNodeInfo} 的 Java 领域封装，
 * 便于在资源层内部处理和聚合。
 */
public class PeerNode {

    private final String nodeId;
    private final String nodeName;
    private final String address;
    private final String domainId;
    private final List<String> tags;

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
        this.address = Objects.requireNonNull(address, "address must not be null");
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

    public void updateCapacity(ResourceCapacity capacity, long version) {
        if (capacity != null) {
            this.capacity = capacity;
        }
        if (version > this.version) {
            this.version = version;
        }
        this.lastSeenMs = Instant.now().toEpochMilli();
    }

    public void touchFromGossip(int gossipCount) {
        this.gossipCount = gossipCount;
        this.lastSeenMs = Instant.now().toEpochMilli();
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
