package com.kekwy.unifabric.fabric.discovery;

import com.kekwy.unifabric.fabric.config.FabricGossipProperties;
import com.kekwy.unifabric.proto.fabric.DiscoveryServiceGrpc;
import com.kekwy.unifabric.proto.fabric.NodeInfoGossipMessage;
import com.kekwy.unifabric.proto.fabric.NodeInfoGossipResponse;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.StatusRuntimeException;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.kekwy.unifabric.fabric.scheduling.SchedulingTopologyChangedEvent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

/**
 * 周期性 Gossip Push-Pull 与低频反熵校准（论文 3.3.2 / 3.3.3）。
 */
@Component
public class GossipScheduler {

    private static final Logger log = LoggerFactory.getLogger(GossipScheduler.class);

    private final NodeDiscoveryManager nodeDiscoveryManager;
    private final FabricGossipProperties gossipProperties;

    @Autowired(required = false)
    private ApplicationEventPublisher eventPublisher;

    private final ConcurrentHashMap<String, ManagedChannel> channels = new ConcurrentHashMap<>();
    private ScheduledExecutorService scheduler;

    public GossipScheduler(NodeDiscoveryManager nodeDiscoveryManager,
                           FabricGossipProperties gossipProperties) {
        this.nodeDiscoveryManager = nodeDiscoveryManager;
        this.gossipProperties = gossipProperties;
    }

    @PostConstruct
    public void start() {
        int interval = gossipProperties.getIntervalSeconds();
        int antiEntropyMin = gossipProperties.getAntiEntropyIntervalMinutes();

        scheduler = Executors.newScheduledThreadPool(2, r -> {
            Thread t = new Thread(r, "fabric-gossip");
            t.setDaemon(true);
            return t;
        });

        if (interval > 0) {
            scheduler.scheduleAtFixedRate(this::gossipRoundSafe, interval, interval, TimeUnit.SECONDS);
            log.info("Gossip 调度已启动: intervalSeconds={}, fanOut={}, seeds={}",
                    interval, gossipProperties.getFanOut(), gossipProperties.getSeedNodes().size());
        } else {
            log.info("Gossip 调度已禁用（intervalSeconds<=0）");
        }

        if (antiEntropyMin > 0) {
            scheduler.scheduleAtFixedRate(this::antiEntropyRoundSafe, antiEntropyMin, antiEntropyMin, TimeUnit.MINUTES);
            log.info("反熵调度已启动: antiEntropyIntervalMinutes={}", antiEntropyMin);
        }
    }

    @PreDestroy
    public void stop() {
        if (scheduler != null) {
            scheduler.shutdownNow();
        }
        channels.values().forEach(ch -> {
            try {
                ch.shutdown();
                if (!ch.awaitTermination(5, TimeUnit.SECONDS)) {
                    ch.shutdownNow();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                ch.shutdownNow();
            }
        });
        channels.clear();
    }

    private void gossipRoundSafe() {
        try {
            gossipRound();
        } catch (Exception e) {
            log.warn("Gossip 轮执行异常: {}", e.getMessage(), e);
        }
    }

    private void antiEntropyRoundSafe() {
        try {
            antiEntropyRound();
        } catch (Exception e) {
            log.warn("反熵轮执行异常: {}", e.getMessage(), e);
        }
    }

    /**
     * 论文算法 1：随机选至多 k 个对等节点，各执行一次 Push-Pull。
     */
    private void gossipRound() {
        List<String> targets = selectGossipTargets();
        for (String address : targets) {
            pushPullExchange(address);
        }
    }

    /**
     * 反熵：低频与随机单节点做一次全量视图交换（论文 3.3.3）。
     */
    private void antiEntropyRound() {
        List<String> targets = selectGossipTargets();
        if (targets.isEmpty()) {
            return;
        }
        String pick = targets.get(ThreadLocalRandom.current().nextInt(targets.size()));
        pushPullExchange(pick);
    }

    private List<String> selectGossipTargets() {
        String localAddr = normalizeAddress(nodeDiscoveryManager.getAdvertiseAddress());
        List<String> peerAddresses = new ArrayList<>();
        for (PeerNode n : nodeDiscoveryManager.getKnownNodes()) {
            if (n.getNodeId().equals(nodeDiscoveryManager.getLocalNodeId())) {
                continue;
            }
            String addr = normalizeAddress(n.getAddress());
            if (addr.isEmpty()) {
                continue;
            }
            if (addr.equals(localAddr)) {
                continue;
            }
            peerAddresses.add(addr);
        }
        Collections.shuffle(peerAddresses, ThreadLocalRandom.current());

        int k = Math.max(1, gossipProperties.getFanOut());
        List<String> chosen = new ArrayList<>();
        for (String a : peerAddresses) {
            if (chosen.size() >= k) {
                break;
            }
            if (!chosen.contains(a)) {
                chosen.add(a);
            }
        }

        if (chosen.isEmpty()) {
            for (String seed : gossipProperties.getSeedNodes()) {
                if (seed == null || seed.isBlank()) {
                    continue;
                }
                String s = normalizeAddress(seed.trim());
                if (s.isEmpty() || s.equals(localAddr)) {
                    continue;
                }
                if (!chosen.contains(s)) {
                    chosen.add(s);
                }
                if (chosen.size() >= k) {
                    break;
                }
            }
        }

        return chosen;
    }

    private void pushPullExchange(String address) {
        String target = normalizeAddress(address);
        if (target.isEmpty()) {
            return;
        }

        ManagedChannel channel = channels.computeIfAbsent(target, this::openChannel);
        if (channel.isShutdown()) {
            channels.remove(target, channel);
            channel = channels.computeIfAbsent(target, this::openChannel);
        }

        NodeInfoGossipMessage request = NodeInfoGossipMessage.newBuilder()
                .setSenderNodeId(nodeDiscoveryManager.getLocalNodeId())
                .setSenderAddress(nodeDiscoveryManager.getAdvertiseAddress())
                .setSenderDomainId(nodeDiscoveryManager.getDomainId())
                .setMessageId(UUID.randomUUID().toString())
                .setTtl(32)
                .addAllNodes(nodeDiscoveryManager.buildGossipSendPayload())
                .build();

        try {
            DiscoveryServiceGrpc.DiscoveryServiceBlockingStub stub =
                    DiscoveryServiceGrpc.newBlockingStub(channel)
                            .withDeadlineAfter(30, TimeUnit.SECONDS);
            NodeInfoGossipResponse response = stub.gossipNodeInfo(request);
            nodeDiscoveryManager.processGossipNodes(response.getNodesList(), target);
            publishSchedulingTopologyChangedIfPresent();
        } catch (StatusRuntimeException e) {
            log.debug("Gossip RPC 失败 target={}: {}", target, e.getStatus());
            channel.shutdown();
            channels.remove(target, channel);
        }
    }

    private ManagedChannel openChannel(String target) {
        return ManagedChannelBuilder.forTarget(target)
                .usePlaintext()
                .keepAliveTime(30, TimeUnit.SECONDS)
                .keepAliveTimeout(10, TimeUnit.SECONDS)
                .build();
    }

    private static String normalizeAddress(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.trim();
    }

    private void publishSchedulingTopologyChangedIfPresent() {
        ApplicationEventPublisher publisher = eventPublisher;
        if (publisher != null) {
            publisher.publishEvent(new SchedulingTopologyChangedEvent(this));
        }
    }
}
