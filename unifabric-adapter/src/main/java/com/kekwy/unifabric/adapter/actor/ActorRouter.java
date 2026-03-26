package com.kekwy.unifabric.adapter.actor;

import com.kekwy.unifabric.proto.actor.ActorEnvelope;
import com.kekwy.unifabric.proto.actor.InvokeRequest;
import com.kekwy.unifabric.proto.actor.InvokeResponse;
import com.kekwy.unifabric.proto.provider.DownstreamGroup;
import com.kekwy.unifabric.proto.provider.RoutingStrategy;
import io.grpc.stub.StreamObserver;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 单机场景下的 Actor 路由：维护 actor_id -> Stream 注册表与按 output_port、DownstreamGroup 的拓扑，
 * 支持 ROUND_ROBIN（含 instance_index 偏移）与 HASH_BY_ROW_ID；就绪/通道建立上报由调用方通过 SignalingService 完成。
 */
@Slf4j
@Component
public class ActorRouter {

    private record DownstreamGroupInfo(
            String logicalOperatorId,
            List<String> actorAddrs,
            RoutingStrategy routingStrategy,
            int outputPort,
            int inputPort
    ) {
    }

    private record ActorRoutingConfig(
            String actorId,
            int instanceIndex,
            Set<String> upstreamActors,
            Set<String> downstreamActors,
            List<DownstreamGroupInfo> downstreamGroups
    ) {
    }

    private final Map<String, StreamObserver<ActorEnvelope>> connectedActors = new HashMap<>();
    private final Map<String, ActorRoutingConfig> routingConfigs = new HashMap<>();
    private final Map<String, AtomicInteger> perActorCounters = new ConcurrentHashMap<>();

    /**
     * 注册 Actor 流（仅登记 stream；调用方应随后上报就绪与通道建立）。
     * 写入 channel 时通过同步包装保证线程安全。
     */
    public Set<ActorEdge> onActorConnected(String actorId, StreamObserver<ActorEnvelope> stream) {
        if (actorId == null || actorId.isBlank()) return Set.of();
        Set<ActorEdge> establishedEdges = new HashSet<>();
        synchronized (connectedActors) {
            connectedActors.put(actorId, new SynchronizedActorChannelObserver(stream));
            ActorRoutingConfig config = routingConfigs.get(actorId);
            if (config == null) {
                return establishedEdges;
            }
            for (String upstreamActorId : config.upstreamActors()) {
                if (connectedActors.containsKey(upstreamActorId)) {
                    establishedEdges.add(new ActorEdge(upstreamActorId, actorId));
                }
            }
            for (String downstreamActorId : config.downstreamActors()) {
                if (connectedActors.containsKey(downstreamActorId)) {
                    establishedEdges.add(new ActorEdge(actorId, downstreamActorId));
                }
            }
        }
        return establishedEdges;
    }

    /**
     * 移除 Actor 流（连接断开时调用）。
     */
    public void onActorLostConnection(String actorId) {
        if (actorId != null && !actorId.isBlank()) {
            synchronized (connectedActors) {
                connectedActors.remove(actorId);
            }
        }
    }

    /**
     * 根据 sourceActorId 与 InvokeResponse.port 解析下游组，按策略选择目标，构造 InvokeRequest（带 input_port）并转发。
     */
    public void routeEnvelope(String sourceActorId, ActorEnvelope envelope) {
        if (envelope == null || envelope.getPayloadCase() != ActorEnvelope.PayloadCase.RESPONSE) return;
        InvokeResponse response = envelope.getResponse();
        ActorRoutingConfig config;
        synchronized (connectedActors) {
            config = routingConfigs.get(sourceActorId);
        }
        if (config == null) {
            log.warn("未找到 sourceActorId 的路由配置: {}", sourceActorId);
            return;
        }

        int port = response.getPort();
        List<DownstreamGroupInfo> groups = config.downstreamGroups().stream()
                .filter(g -> g.outputPort() == port)
                .toList();

        for (DownstreamGroupInfo group : groups) {
            List<String> alive = filterAlive(group.actorAddrs());
            if (alive.isEmpty()) {
                log.warn("output_port={} 下 logicalOperatorId={} 无可用下游实例", port, group.logicalOperatorId());
                continue;
            }

            String target = selectTarget(sourceActorId, envelope, group, alive);
            InvokeRequest request = InvokeRequest.newBuilder()
                    .setExecutionId(response.getExecutionId())
                    .setRow(response.getRow())
                    .setInputPort(group.inputPort())
                    .build();
            ActorEnvelope toSend = ActorEnvelope.newBuilder().setRequest(request).build();
            deliverTo(target, toSend);
        }
    }

    private List<String> filterAlive(List<String> actorAddrs) {
        synchronized (connectedActors) {
            List<String> out = new ArrayList<>();
            for (String addr : actorAddrs) {
                if (connectedActors.containsKey(addr)) {
                    out.add(addr);
                }
            }
            return out;
        }
    }

    private String selectTarget(String sourceActorId, ActorEnvelope envelope,
                                DownstreamGroupInfo group, List<String> alive) {
        return switch (group.routingStrategy()) {
            case ROUND_ROBIN -> roundRobinSelect(sourceActorId, group, alive);
            case HASH_BY_ROW_ID -> hashSelect(envelope, alive);
            default -> alive.get(0);
        };
    }

    private String roundRobinSelect(String sourceActorId, DownstreamGroupInfo group, List<String> alive) {
        int offset = getInstanceIndex(sourceActorId);
        String counterKey = sourceActorId + ":" + group.logicalOperatorId();
        int seq = perActorCounters
                .computeIfAbsent(counterKey, k -> new AtomicInteger(0))
                .getAndIncrement();
        int idx = Math.floorMod(offset + seq, alive.size());
        return alive.get(idx);
    }

    private String hashSelect(ActorEnvelope envelope, List<String> alive) {
        String rowId = (envelope.getPayloadCase() == ActorEnvelope.PayloadCase.RESPONSE && envelope.getResponse().hasRow())
                ? envelope.getResponse().getRow().getRowId() : "";
        int hash = murmur3_32(rowId.getBytes(StandardCharsets.UTF_8));
        int idx = Math.floorMod(hash, alive.size());
        return alive.get(idx);
    }

    /** 简单的 32 位 MurmurHash3 实现，保证确定性。 */
    private static int murmur3_32(byte[] data) {
        int len = data.length;
        int c1 = 0xcc9e2d51;
        int c2 = 0x1b873593;
        int h1 = 0;
        int i = 0;
        while (i + 4 <= len) {
            int k1 = (data[i] & 0xff) | ((data[i + 1] & 0xff) << 8)
                    | ((data[i + 2] & 0xff) << 16) | ((data[i + 3] & 0xff) << 24);
            i += 4;
            k1 *= c1;
            k1 = Integer.rotateLeft(k1, 15);
            k1 *= c2;
            h1 ^= k1;
            h1 = Integer.rotateLeft(h1, 13);
            h1 = h1 * 5 + 0xe6546b64;
        }
        int k1 = 0;
        if (i < len) k1 = data[i] & 0xff;
        if (i + 1 < len) k1 |= (data[i + 1] & 0xff) << 8;
        if (i + 2 < len) k1 |= (data[i + 2] & 0xff) << 16;
        k1 *= c1;
        k1 = Integer.rotateLeft(k1, 15);
        k1 *= c2;
        h1 ^= k1;
        h1 ^= len;
        h1 ^= h1 >>> 16;
        h1 *= 0x85ebca6b;
        h1 ^= h1 >>> 13;
        h1 *= 0xc2b2ae35;
        h1 ^= h1 >>> 16;
        return h1;
    }

    private int getInstanceIndex(String actorId) {
        ActorRoutingConfig config = routingConfigs.get(actorId);
        return config != null ? config.instanceIndex() : 0;
    }

    private void deliverTo(String targetActorId, ActorEnvelope envelope) {
        deliverToTarget(targetActorId, envelope);
    }

    /**
     * 直接向本地已注册的 actor 投递 envelope（用于跨 Provider 转发时 envelope 已带物理 target）。
     * 发送通过 SynchronizedActorChannelObserver 加锁，保证线程安全。
     */
    public void deliverToTarget(String targetActorId, ActorEnvelope envelope) {
        StreamObserver<ActorEnvelope> stream;
        synchronized (connectedActors) {
            stream = connectedActors.get(targetActorId);
        }
        if (stream == null) {
            log.warn("目标 Actor 未注册，无法转发: target={}", targetActorId);
            return;
        }
        try {
            stream.onNext(envelope);
            log.debug("已路由 envelope 到 target={}, payloadCase={}", targetActorId, envelope.getPayloadCase());
        } catch (Exception e) {
            log.warn("路由 envelope 到 target={} 失败", targetActorId, e);
        }
    }

    /**
     * 注册 Actor 路由拓扑：instance_index 用于 round-robin 偏移，downstream_groups 用于按 output_port 与策略路由。
     */
    public void registerActorRouting(String actorId,
                                    int instanceIndex,
                                    List<String> upstreamActorIds,
                                    List<DownstreamGroup> downstreamGroups) {
        if (actorId == null || actorId.isBlank()) return;
        if (downstreamGroups == null) downstreamGroups = Collections.emptyList();

        Set<String> upstreams = upstreamActorIds != null ? new HashSet<>(upstreamActorIds) : Set.of();
        Set<String> downstreams = new HashSet<>();
        List<DownstreamGroupInfo> groups = new ArrayList<>();

        for (DownstreamGroup g : downstreamGroups) {
            List<String> addrs = g.getActorAddrsList();
            downstreams.addAll(addrs);
            groups.add(new DownstreamGroupInfo(
                    g.getLogicalOperatorId(),
                    List.copyOf(addrs),
                    g.getRoutingStrategy(),
                    g.getOutputPort(),
                    g.getInputPort()
            ));
        }

        synchronized (connectedActors) {
            routingConfigs.put(actorId, new ActorRoutingConfig(
                    actorId,
                    instanceIndex,
                    upstreams,
                    downstreams,
                    groups
            ));
        }
    }

    /**
     * 对 Actor Channel 的 StreamObserver 做同步包装，保证多线程向同一 channel 发送 onNext 时线程安全。
     */
    private static final class SynchronizedActorChannelObserver implements StreamObserver<ActorEnvelope> {
        private final Object lock = new Object();
        private final StreamObserver<ActorEnvelope> delegate;

        SynchronizedActorChannelObserver(StreamObserver<ActorEnvelope> delegate) {
            this.delegate = delegate;
        }

        @Override
        public void onNext(ActorEnvelope value) {
            synchronized (lock) {
                delegate.onNext(value);
            }
        }

        @Override
        public void onError(Throwable t) {
            synchronized (lock) {
                delegate.onError(t);
            }
        }

        @Override
        public void onCompleted() {
            synchronized (lock) {
                delegate.onCompleted();
            }
        }
    }
}
