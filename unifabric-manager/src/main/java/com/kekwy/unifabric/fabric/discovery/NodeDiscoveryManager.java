package com.kekwy.unifabric.fabric.discovery;

import com.kekwy.unifabric.proto.ir.Resource;
import com.kekwy.unifabric.proto.resource.PeerNodeInfo;
import com.kekwy.unifabric.proto.resource.ResourceCapacity;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 节点发现与资源视图管理器（Java 版）。
 * <p>
 * 职责：
 * <ul>
 *   <li>维护本地节点信息（local node）</li>
 *   <li>维护已知的其它控制节点 {@link PeerNode}</li>
 *   <li>处理来自 Gossip RPC 的 {@link PeerNodeInfo} 列表，更新视图</li>
 *   <li>提供聚合资源视图（所有节点的总/已用/可用）</li>
 * </ul>
 *
 * 注意：本类不直接负责 gRPC 通信，只负责内存中的数据结构和合并规则。
 * gRPC 层会调用 {@link #processGossipNodes(List, String)} 等方法。
 */
public class NodeDiscoveryManager {

    private final String localNodeId;
    private final String domainId;
    private final Duration nodeTtl;

    private volatile PeerNode localNode;

    private final Map<String, PeerNode> knownNodes = new ConcurrentHashMap<>();

    public NodeDiscoveryManager(String localNodeId,
                                String domainId,
                                Duration nodeTtl) {
        this.localNodeId = Objects.requireNonNull(localNodeId, "localNodeId must not be null");
        this.domainId = domainId != null ? domainId : "";
        this.nodeTtl = nodeTtl != null ? nodeTtl : Duration.ofMinutes(5);
    }

    // ======================== 本地节点 ========================

    /**
     * 初始化或更新本地节点信息。
     */
    public synchronized void updateLocalNode(String nodeName,
                                             String address,
                                             ResourceCapacity capacity,
                                             List<String> tags,
                                             long version) {
        ResourceCapacity cap = capacity != null ? capacity : emptyCapacity();
        PeerNode node = new PeerNode(
                localNodeId,
                nodeName != null ? nodeName : localNodeId,
                address,
                domainId,
                tags,
                cap,
                version,
                0,
                Instant.now().toEpochMilli()
        );
        this.localNode = node;
        // 本地节点也加入 knownNodes，便于统一聚合
        knownNodes.put(localNodeId, node);
    }

    public Optional<PeerNode> getLocalNode() {
        return Optional.ofNullable(localNode);
    }

    // ======================== Gossip 处理 ========================

    /**
     * 处理从某个 peer 接收到的一批 {@link PeerNodeInfo}。
     *
     * @param nodes          对端提供的节点列表
     * @param senderAddress  gRPC 对端地址（用于缺失 address 时填充）
     */
    public void processGossipNodes(List<PeerNodeInfo> nodes, String senderAddress) {
        if (nodes == null || nodes.isEmpty()) {
            return;
        }
        long now = Instant.now().toEpochMilli();
        for (PeerNodeInfo info : nodes) {
            if (info == null || info.getNodeId().isEmpty()) {
                continue;
            }
            // 只关注当前 domain
            if (!info.getDomainId().isEmpty() && !info.getDomainId().equals(domainId)) {
                continue;
            }
            String nodeId = info.getNodeId();
            // 忽略本地节点（由 updateLocalNode 维护）
            if (nodeId.equals(localNodeId)) {
                continue;
            }

            knownNodes.compute(nodeId, (id, existing) -> {
                String address = info.getAddress().isEmpty() ? senderAddress : info.getAddress();
                ResourceCapacity cap = info.hasCapacity() ? info.getCapacity() : emptyCapacity();
                long version = info.getVersion();
                int gossipCount = info.getGossipCount();
                if (existing == null) {
                    return new PeerNode(
                            nodeId,
                            info.getNodeName(),
                            address,
                            info.getDomainId(),
                            info.getTagsList(),
                            cap,
                            version,
                            gossipCount,
                            now
                    );
                } else {
                    // 简单基于 version 进行“最后写入优先”的合并
                    if (version >= existing.getVersion()) {
                        existing.updateCapacity(cap, version);
                    }
                    existing.touchFromGossip(gossipCount);
                    return existing;
                }
            });
        }
        // 清理过期节点
        cleanupExpired(now);
    }

    private void cleanupExpired(long nowMs) {
        long ttlMs = nodeTtl.toMillis();
        knownNodes.values().removeIf(node -> {
            if (node.getNodeId().equals(localNodeId)) {
                return false;
            }
            return nowMs - node.getLastSeenMs() > ttlMs;
        });
    }

    // ======================== 视图查询 ========================

    /**
     * 返回当前已知的所有节点（包括本地节点）。
     */
    public List<PeerNode> getKnownNodes() {
        return new ArrayList<>(knownNodes.values());
    }

    /**
     * 计算所有节点的聚合资源容量。
     */
    public ResourceCapacity getAggregatedCapacity() {
        double totalCpu = 0;
        double totalGpu = 0;
        long totalMem = 0;

        double usedCpu = 0;
        double usedGpu = 0;
        long usedMem = 0;

        for (PeerNode node : knownNodes.values()) {
            ResourceCapacity cap = node.getCapacity();
            if (cap == null) {
                continue;
            }
            Resource total = cap.getTotal();
            Resource used = cap.getUsed();
            if (total != null) {
                totalCpu += total.getCpu();
                totalGpu += total.getGpu();
                totalMem += parseMemoryBytes(total.getMemory());
            }
            if (used != null) {
                usedCpu += used.getCpu();
                usedGpu += used.getGpu();
                usedMem += parseMemoryBytes(used.getMemory());
            }
        }

        long availableMem = Math.max(0, totalMem - usedMem);
        double availableCpu = Math.max(0, totalCpu - usedCpu);
        double availableGpu = Math.max(0, totalGpu - usedGpu);

        return ResourceCapacity.newBuilder()
                .setTotal(Resource.newBuilder()
                        .setCpu(totalCpu)
                        .setMemory(Long.toString(totalMem))
                        .setGpu(totalGpu)
                        .build())
                .setUsed(Resource.newBuilder()
                        .setCpu(usedCpu)
                        .setMemory(Long.toString(usedMem))
                        .setGpu(usedGpu)
                        .build())
                .setAvailable(Resource.newBuilder()
                        .setCpu(availableCpu)
                        .setMemory(Long.toString(availableMem))
                        .setGpu(availableGpu)
                        .build())
                .build();
    }

    /**
     * 根据简单策略选择一个“最适合”的远程节点：
     * <ul>
     *   <li>排除本地节点</li>
     *   <li>按 Available CPU 从大到小排序，取第一个</li>
     * </ul>
     */
    public Optional<PeerNode> chooseRemoteNodeByAvailableCpu() {
        return knownNodes.values().stream()
                .filter(node -> !node.getNodeId().equals(localNodeId))
                .max((a, b) -> {
                    double aCpu = safeCapacity(a).getAvailable().getCpu();
                    double bCpu = safeCapacity(b).getAvailable().getCpu();
                    return Double.compare(aCpu, bCpu);
                });
    }

    private ResourceCapacity safeCapacity(PeerNode node) {
        return node.getCapacity() != null ? node.getCapacity() : emptyCapacity();
    }

    private static ResourceCapacity emptyCapacity() {
        Resource zero = Resource.newBuilder()
                .setCpu(0)
                .setMemory("0")
                .setGpu(0)
                .build();
        return ResourceCapacity.newBuilder()
                .setTotal(zero)
                .setUsed(zero)
                .setAvailable(zero)
                .build();
    }

    private static long parseMemoryBytes(String memory) {
        if (memory == null || memory.isBlank()) return 0;
        try {
            return Long.parseLong(memory.replaceAll("[^\\d]", ""));
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
