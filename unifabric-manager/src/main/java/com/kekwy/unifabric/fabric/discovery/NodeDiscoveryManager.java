package com.kekwy.unifabric.fabric.discovery;

import com.kekwy.unifabric.fabric.provider.ProviderInfo;
import com.kekwy.unifabric.fabric.provider.ProviderRegistry;
import com.kekwy.unifabric.proto.common.ResourceCapacity;
import com.kekwy.unifabric.proto.common.ResourceSpec;
import com.kekwy.unifabric.proto.fabric.PeerNodeInfo;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * 节点发现与资源视图管理器。
 * <p>
 * {@link #refreshFromProviderRegistry(ProviderRegistry)} 将本域内 Provider 资源聚合为域级视图（论文 3.2.3）。
 * {@link #processGossipNodes(List, String)} 按论文 3.3.2 版本化合并跨域 Gossip 条目。
 */
public class NodeDiscoveryManager {

    private final String localNodeId;
    private final String domainId;
    private final Duration nodeTtl;
    private final String advertiseAddress;

    private final AtomicLong localViewVersion = new AtomicLong(0);

    private volatile PeerNode localNode;

    private final Map<String, PeerNode> knownNodes = new ConcurrentHashMap<>();

    public NodeDiscoveryManager(String localNodeId,
                                String domainId,
                                Duration nodeTtl,
                                String advertiseAddress) {
        this.localNodeId = Objects.requireNonNull(localNodeId, "localNodeId must not be null");
        this.domainId = domainId != null ? domainId : "";
        this.nodeTtl = nodeTtl != null ? nodeTtl : Duration.ofMinutes(5);
        this.advertiseAddress = advertiseAddress != null ? advertiseAddress : "";
    }

    /**
     * 根据当前 Provider 注册表聚合本域资源容量并更新本地节点视图。
     */
    public synchronized void refreshFromProviderRegistry(ProviderRegistry registry) {
        if (registry == null) {
            return;
        }
        long version = localViewVersion.incrementAndGet();
        ResourceCapacity aggregated = aggregateProviderCapacities(registry);
        List<String> tags = collectDistinctTags(registry);
        updateLocalNode(localNodeId, advertiseAddress.isEmpty() ? localNodeId : advertiseAddress,
                aggregated, tags, version);
    }

    private static ResourceCapacity aggregateProviderCapacities(ProviderRegistry registry) {
        double totalCpu = 0;
        double totalGpu = 0;
        long totalMem = 0;
        double usedCpu = 0;
        double usedGpu = 0;
        long usedMem = 0;

        for (ProviderInfo p : registry.listProviders()) {
            if (p.getStatus() != ProviderInfo.Status.ONLINE && p.getStatus() != ProviderInfo.Status.DEGRADED) {
                continue;
            }
            ResourceCapacity c = p.getResourceCapacity();
            if (c == null || ResourceCapacity.getDefaultInstance().equals(c)) {
                continue;
            }
            ResourceSpec t = c.getTotal();
            ResourceSpec u = c.getUsed();
            if (t != null) {
                totalCpu += t.getCpu();
                totalGpu += t.getGpu();
                totalMem += parseMemoryBytes(t.getMemory());
            }
            if (u != null) {
                usedCpu += u.getCpu();
                usedGpu += u.getGpu();
                usedMem += parseMemoryBytes(u.getMemory());
            }
        }

        long availableMem = Math.max(0, totalMem - usedMem);
        double availableCpu = Math.max(0, totalCpu - usedCpu);
        double availableGpu = Math.max(0, totalGpu - usedGpu);

        return ResourceCapacity.newBuilder()
                .setTotal(ResourceSpec.newBuilder()
                        .setCpu(totalCpu)
                        .setMemory(Long.toString(totalMem))
                        .setGpu(totalGpu)
                        .build())
                .setUsed(ResourceSpec.newBuilder()
                        .setCpu(usedCpu)
                        .setMemory(Long.toString(usedMem))
                        .setGpu(usedGpu)
                        .build())
                .setAvailable(ResourceSpec.newBuilder()
                        .setCpu(availableCpu)
                        .setMemory(Long.toString(availableMem))
                        .setGpu(availableGpu)
                        .build())
                .build();
    }

    private static List<String> collectDistinctTags(ProviderRegistry registry) {
        Set<String> set = new LinkedHashSet<>();
        for (ProviderInfo p : registry.listProviders()) {
            if (p.getStatus() != ProviderInfo.Status.ONLINE && p.getStatus() != ProviderInfo.Status.DEGRADED) {
                continue;
            }
            for (String t : p.getTags()) {
                if (t != null && !t.isBlank()) {
                    set.add(t);
                }
            }
        }
        return List.copyOf(set);
    }

    public synchronized void updateLocalNode(String nodeName,
                                             String address,
                                             ResourceCapacity capacity,
                                             List<String> tags,
                                             long version) {
        ResourceCapacity cap = capacity != null ? capacity : emptyCapacity();
        String addr = address != null ? address : "";
        PeerNode node = new PeerNode(
                localNodeId,
                nodeName != null ? nodeName : localNodeId,
                addr,
                domainId,
                tags,
                cap,
                version,
                0,
                Instant.now().toEpochMilli()
        );
        this.localNode = node;
        knownNodes.put(localNodeId, node);
    }

    public Optional<PeerNode> getLocalNode() {
        return Optional.ofNullable(localNode);
    }

    /**
     * 合并对端 Gossip 条目（论文算法 2：版本优先；同版本仅刷新观测时间与传播计数）。
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
            String nodeId = info.getNodeId();
            if (nodeId.equals(localNodeId)) {
                continue;
            }

            String address = (info.getAddress() == null || info.getAddress().isEmpty())
                    ? (senderAddress != null ? senderAddress : "")
                    : info.getAddress();
            ResourceCapacity cap = info.hasCapacity() ? info.getCapacity() : emptyCapacity();
            long version = info.getVersion();
            int gossipCount = info.getGossipCount();
            long incomingLastSeen = info.getLastSeenMs() > 0 ? info.getLastSeenMs() : now;

            knownNodes.compute(nodeId, (id, existing) -> {
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
                            incomingLastSeen
                    );
                }
                if (version > existing.getVersion()) {
                    existing.updateFromGossip(
                            info.getNodeName(),
                            address,
                            info.getDomainId(),
                            info.getTagsList(),
                            cap,
                            version,
                            gossipCount,
                            incomingLastSeen
                    );
                    return existing;
                }
                if (version == existing.getVersion()) {
                    existing.refreshIfNewerObservation(incomingLastSeen, gossipCount);
                    return existing;
                }
                return existing;
            });
        }
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

    /**
     * 当前视图的只读快照（响应 Gossip 时返回本地视图，不递增传播计数）。
     */
    public List<PeerNodeInfo> buildNodeInfoList() {
        List<PeerNodeInfo> out = new ArrayList<>(knownNodes.size());
        for (PeerNode n : knownNodes.values()) {
            out.add(toPeerNodeInfo(n));
        }
        return out;
    }

    /**
     * 出站 Gossip：递增各条目的传播计数后序列化（论文 3.3.2）。
     */
    public List<PeerNodeInfo> buildGossipSendPayload() {
        List<PeerNode> snapshot = new ArrayList<>(knownNodes.values());
        List<PeerNodeInfo> out = new ArrayList<>(snapshot.size());
        for (PeerNode n : snapshot) {
            int gc = n.incrementGossipCountForSend();
            out.add(toPeerNodeInfoWithGossipCount(n, gc));
        }
        return out;
    }

    private static PeerNodeInfo toPeerNodeInfo(PeerNode n) {
        return toPeerNodeInfoWithGossipCount(n, n.getGossipCount());
    }

    private static PeerNodeInfo toPeerNodeInfoWithGossipCount(PeerNode n, int gossipCount) {
        ResourceCapacity cap = n.getCapacity() != null ? n.getCapacity() : emptyCapacity();
        return PeerNodeInfo.newBuilder()
                .setNodeId(n.getNodeId())
                .setNodeName(n.getNodeName() != null ? n.getNodeName() : n.getNodeId())
                .setAddress(n.getAddress() != null ? n.getAddress() : "")
                .setDomainId(n.getDomainId() != null ? n.getDomainId() : "")
                .setCapacity(cap)
                .addAllTags(n.getTags() != null ? n.getTags() : List.of())
                .setVersion(n.getVersion())
                .setGossipCount(gossipCount)
                .setLastSeenMs(n.getLastSeenMs())
                .build();
    }

    /**
     * 按资源需求与标签筛选候选管理节点（远程域），按可用 CPU 降序（论文 3.3.3 候选节点筛选）。
     *
     * @param required     可为 null，表示不做资源下界过滤；某维度 &le; 0 视为无约束
     * @param requiredTags 可为 null 或空，表示必须同时具备的标签集合
     */
    public List<PeerNode> filterCandidateNodes(ResourceSpec required, List<String> requiredTags) {
        List<String> tags = requiredTags == null ? List.of() : requiredTags.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();

        return knownNodes.values().stream()
                .filter(node -> !node.getNodeId().equals(localNodeId))
                .filter(node -> matchesResourceConstraints(node, required))
                .filter(node -> matchesAllTags(node, tags))
                .sorted(Comparator.comparingDouble((PeerNode n) ->
                        safeCapacity(n).getAvailable().getCpu()).reversed())
                .collect(Collectors.toList());
    }

    private static boolean matchesAllTags(PeerNode node, List<String> requiredTags) {
        if (requiredTags.isEmpty()) {
            return true;
        }
        Set<String> nodeTags = new LinkedHashSet<>(node.getTags() != null ? node.getTags() : List.of());
        for (String t : requiredTags) {
            if (!nodeTags.contains(t)) {
                return false;
            }
        }
        return true;
    }

    private boolean matchesResourceConstraints(PeerNode node, ResourceSpec required) {
        if (required == null) {
            return true;
        }
        ResourceCapacity cap = safeCapacity(node);
        ResourceSpec avail = cap.getAvailable();
        if (required.getCpu() > 0 && avail.getCpu() < required.getCpu()) {
            return false;
        }
        if (required.getGpu() > 0 && avail.getGpu() < required.getGpu()) {
            return false;
        }
        if (required.getMemory() != null && !required.getMemory().isBlank()) {
            long needMem = parseMemoryBytes(required.getMemory());
            long haveMem = parseMemoryBytes(avail.getMemory());
            if (needMem > 0 && haveMem < needMem) {
                return false;
            }
        }
        return true;
    }

    public List<PeerNode> getKnownNodes() {
        return new ArrayList<>(knownNodes.values());
    }

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
            ResourceSpec t = cap.getTotal();
            ResourceSpec u = cap.getUsed();
            if (t != null) {
                totalCpu += t.getCpu();
                totalGpu += t.getGpu();
                totalMem += parseMemoryBytes(t.getMemory());
            }
            if (u != null) {
                usedCpu += u.getCpu();
                usedGpu += u.getGpu();
                usedMem += parseMemoryBytes(u.getMemory());
            }
        }

        long availableMem = Math.max(0, totalMem - usedMem);
        double availableCpu = Math.max(0, totalCpu - usedCpu);
        double availableGpu = Math.max(0, totalGpu - usedGpu);

        return ResourceCapacity.newBuilder()
                .setTotal(ResourceSpec.newBuilder()
                        .setCpu(totalCpu)
                        .setMemory(Long.toString(totalMem))
                        .setGpu(totalGpu)
                        .build())
                .setUsed(ResourceSpec.newBuilder()
                        .setCpu(usedCpu)
                        .setMemory(Long.toString(usedMem))
                        .setGpu(usedGpu)
                        .build())
                .setAvailable(ResourceSpec.newBuilder()
                        .setCpu(availableCpu)
                        .setMemory(Long.toString(availableMem))
                        .setGpu(availableGpu)
                        .build())
                .build();
    }

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
        ResourceSpec zero = ResourceSpec.newBuilder()
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

    public String getLocalNodeId() {
        return localNodeId;
    }

    public String getDomainId() {
        return domainId;
    }

    public String getAdvertiseAddress() {
        return advertiseAddress;
    }
}
