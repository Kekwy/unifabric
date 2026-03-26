package com.kekwy.unifabric.fabric.actor;

/**
 * 记录一个已连接 Actor 的元数据与实时状态。
 * <p>
 * 在 Actor 上报 ready 时创建，在控制通道断开时失效。
 */
public class ActorSession {

    public enum Status {
        READY,
        DRAINING,
        ERROR
    }

    private final String actorId;
    private final String providerId;
    private volatile Status status;

    public ActorSession(String actorId, String providerId) {
        this.actorId = actorId;
        this.providerId = providerId;
        this.status = Status.READY;
    }

    public String getActorId() {
        return actorId;
    }

    /**
     * 部署该 Actor 的 Provider 标识，用于通过 SignalingChannel 转发消息。
     */
    public String getProviderId() {
        return providerId;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    @Override
    public String toString() {
        return "ActorSession{actorId=" + actorId + ", providerId=" + providerId + ", status=" + status + "}";
    }
}
