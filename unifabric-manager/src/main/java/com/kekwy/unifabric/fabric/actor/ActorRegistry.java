package com.kekwy.unifabric.fabric.actor;

import com.kekwy.unifabric.proto.actor.ActorEnvelope;

import java.util.List;

/**
 * Actor 注册表：维护所有已上报 ready 的 Actor 会话信息。
 * <p>
 * 由 control-plane 持有，供 gRPC 服务、调度器等组件使用。
 */
public interface ActorRegistry {

    /**
     * Actor 上报 ready 时调用。
     *
     * @param actorId    Actor 标识
     * @param providerId 部署该 Actor 的 Provider 标识
     */
    void onActorReady(String actorId, String providerId);

    /**
     * Actor 间数据通道连通时调用（由 gRPC 服务层转发 ActorChannelStatus）。
     */
    void onChannelConnected(String srcActorId, String dstActorId);

    /**
     * Actor 连接断开时调用。
     */
    void onActorDisconnected(String actorId);

    /**
     * 向指定 Actor 发送消息。
     *
     * @throws IllegalStateException 若该 Actor 无活跃连接
     */
    void sendToActor(String actorId, ActorEnvelope envelope);

    /**
     * 获取指定 Actor 的会话信息，未注册则返回 null。
     */
    ActorSession getSession(String actorId);

    /**
     * 列出所有在线 Actor 会话。
     */
    List<ActorSession> listSessions();

    /**
     * 注册 Actor 生命周期事件监听器。
     */
    void addListener(ActorLifecycleListener listener);

    /**
     * 移除监听器。
     */
    void removeListener(ActorLifecycleListener listener);
}
