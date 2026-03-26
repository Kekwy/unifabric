package com.kekwy.unifabric.fabric.actor;

/**
 * 上层消费 Actor 生命周期事件的钩子。
 * <p>
 * 通过 {@link ActorRegistry#addListener(ActorLifecycleListener)} 注册后，
 * 当 Actor 就绪或断连时会收到回调。
 */
public interface ActorLifecycleListener {

    /**
     * Actor 已启动并就绪（Provider 上报 ActorReadyReport 后触发）。
     *
     * @param actorId    Actor 标识
     * @param providerId 部署该 Actor 的 Provider 标识
     */
    void onActorReady(String actorId, String providerId);

    /**
     * Actor 间数据通道已建立（Provider 上报 ActorChannelStatus.connected=true 后触发）。
     */
    void onChannelConnected(String srcActorId, String dstActorId);

    /**
     * Actor 控制通道已断开。
     */
    void onActorDisconnected(String actorId);
}
