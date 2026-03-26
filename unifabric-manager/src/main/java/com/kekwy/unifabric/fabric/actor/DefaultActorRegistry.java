package com.kekwy.unifabric.fabric.actor;

import com.kekwy.unifabric.fabric.provider.ProviderConnection;
import com.kekwy.unifabric.fabric.provider.ProviderRegistry;
import com.kekwy.unifabric.proto.actor.ActorEnvelope;
import com.kekwy.unifabric.proto.provider.ActorMessageForward;
import com.kekwy.unifabric.proto.provider.SignalingEnvelope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import jakarta.annotation.PreDestroy;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * {@link ActorRegistry} 的默认实现。
 * <p>
 * 维护 Actor 会话（含 providerId），消息通过部署该 Actor 的 Provider 的 SignalingChannel
 * 多路复用转发（target_actor_id + actor_envelope_forward）。
 */
@Service
public class DefaultActorRegistry implements ActorRegistry {

    private static final Logger log = LoggerFactory.getLogger(DefaultActorRegistry.class);

    private final ProviderRegistry providerRegistry;
    private final ConcurrentHashMap<String, ActorSession> sessions = new ConcurrentHashMap<>();
    private final CopyOnWriteArrayList<ActorLifecycleListener> listeners = new CopyOnWriteArrayList<>();

    public DefaultActorRegistry(ProviderRegistry providerRegistry) {
        this.providerRegistry = providerRegistry;
    }

    @PreDestroy
    public void shutdown() {
        sessions.clear();
    }

    @Override
    public void onActorReady(String actorId, String providerId) {
        ActorSession oldSession = sessions.get(actorId);
        if (oldSession != null) {
            log.debug("Actor 重新上报 ready，更新 session: actorId={}", actorId);
        }

        ActorSession session = new ActorSession(actorId, providerId);
        sessions.put(actorId, session);

        log.info("Actor ready: actorId={}, providerId={}", actorId, providerId);

        for (ActorLifecycleListener listener : listeners) {
            try {
                listener.onActorReady(actorId, providerId);
            } catch (Exception e) {
                log.warn("ActorLifecycleListener.onActorReady 异常: actorId={}", actorId, e);
            }
        }
    }

    @Override
    public void onChannelConnected(String srcActorId, String dstActorId) {
        log.info("Channel connected: src={}, dst={}", srcActorId, dstActorId);

        for (ActorLifecycleListener listener : listeners) {
            try {
                listener.onChannelConnected(srcActorId, dstActorId);
            } catch (Exception e) {
                log.warn("ActorLifecycleListener.onChannelConnected 异常: src={}, dst={}",
                        srcActorId, dstActorId, e);
            }
        }
    }

    @Override
    public void onActorDisconnected(String actorId) {
        ActorSession session = sessions.remove(actorId);
        if (session != null) {
            log.info("Actor 已断开: actorId={}", actorId);
        }

        for (ActorLifecycleListener listener : listeners) {
            try {
                listener.onActorDisconnected(actorId);
            } catch (Exception e) {
                log.warn("ActorLifecycleListener.onActorDisconnected 异常: actorId={}", actorId, e);
            }
        }
    }

    @Override
    public void sendToActor(String actorId, ActorEnvelope envelope) {
        ActorSession session = sessions.get(actorId);
        if (session == null) {
            throw new IllegalStateException("Actor 未就绪或已断开: " + actorId);
        }
        String providerId = session.getProviderId();
        ProviderConnection conn = providerRegistry.getConnection(providerId);
        if (conn == null || conn.isClosed()) {
            throw new IllegalStateException("Provider 无活跃连接: " + providerId + ", actorId=" + actorId);
        }

        ActorMessageForward actorMessageForward = ActorMessageForward.newBuilder()
                .setTarget(actorId)
                .setActorEnvelope(envelope)
                .build();

        SignalingEnvelope signaling = SignalingEnvelope.newBuilder()
                .setActorMessageForward(actorMessageForward)
                .setTimestampMs(System.currentTimeMillis())
                .build();
        conn.sendSignaling(signaling);
    }

    @Override
    public ActorSession getSession(String actorId) {
        return sessions.get(actorId);
    }

    @Override
    public List<ActorSession> listSessions() {
        return List.copyOf(sessions.values());
    }

    @Override
    public void addListener(ActorLifecycleListener listener) {
        listeners.add(listener);
    }

    @Override
    public void removeListener(ActorLifecycleListener listener) {
        listeners.remove(listener);
    }
}
