package com.kekwy.unifabric.fabric.provider;

import com.kekwy.unifabric.proto.provider.ControlEnvelope;
import com.kekwy.unifabric.proto.provider.DeploymentEnvelope;
import com.kekwy.unifabric.proto.provider.RegisterProviderRequest;
import com.kekwy.unifabric.proto.provider.SignalingEnvelope;
import io.grpc.stub.StreamObserver;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Provider 注册表：维护所有已注册 Provider 的状态与通道连接。
 * <p>
 * 由 control-plane 持有，供 gRPC 服务、调度器等组件使用。
 */
public interface ProviderRegistry {

    /**
     * 注册新的 Provider，返回分配的 provider_id。
     */
    String register(RegisterProviderRequest request);

    /**
     * 注销 Provider。
     */
    void deregister(String providerId);

    /**
     * 处理心跳，更新最后活跃时间。
     */
    void heartbeat(String providerId);

    /**
     * 由心跳携带的标签快照更新 Provider 标签（论文 3.2.3）。
     */
    void updateTags(String providerId, List<String> tags);

    /**
     * 处理资源容量上报（控制通道 {@link com.kekwy.unifabric.proto.provider.ResourceCapacityReport}）。
     */
    void updateResourceCapacity(String providerId, com.kekwy.unifabric.proto.common.ResourceCapacity capacity);

    // ======================== ControlChannel ========================

    void openControlChannel(String providerId, StreamObserver<ControlEnvelope> controlSender);

    void closeControlChannel(String providerId, StreamObserver<ControlEnvelope> senderThatClosed);

    // ======================== SignalingChannel ========================

    void openSignalingChannel(String providerId, StreamObserver<SignalingEnvelope> signalingSender);

    void closeSignalingChannel(String providerId, StreamObserver<SignalingEnvelope> senderThatClosed);

    // ======================== DeploymentChannel ========================

    /**
     * 建立 DeploymentChannel 连接。
     *
     * @param providerId       Provider 标识
     * @param deploymentSender 用于向 Provider 推送 DeploymentEnvelope 的流
     */
    void openDeploymentChannel(String providerId, StreamObserver<DeploymentEnvelope> deploymentSender);

    /**
     * 处理 Provider 回传的部署响应。
     */
    void handleDeploymentResponse(String providerId, DeploymentEnvelope response);

    /**
     * 关闭 DeploymentChannel 连接：仅当当前持有的 sender 即为 {@code senderThatClosed} 时置空，
     * 避免旧流晚于新流关闭时误清掉新连接。
     *
     * @param providerId       Provider 标识
     * @param senderThatClosed 刚关闭的流对应的发送端（与 openDeploymentChannel 传入的 observer 一致）
     */
    void closeDeploymentChannel(String providerId, StreamObserver<DeploymentEnvelope> senderThatClosed);

    /**
     * 通过 DeploymentChannel 向指定 Provider 发送部署请求，异步获取响应。
     */
    CompletableFuture<DeploymentEnvelope> sendDeployment(String providerId, DeploymentEnvelope.Builder envelopeBuilder);

    // ======================== 查询 ========================

    /**
     * 获取指定 Provider 信息。
     */
    ProviderInfo getProvider(String providerId);

    /**
     * 列出所有已注册的 Provider。
     */
    List<ProviderInfo> listProviders();

    /**
     * 列出所有在线且已建立 DeploymentChannel 的 Provider。
     */
    List<ProviderInfo> listOnlineProviders();

    /**
     * 按类型筛选在线 Provider（如 "docker"、"k8s"）。
     */
    List<ProviderInfo> listOnlineProvidersByType(String providerType);

    /**
     * 获取指定 Provider 的连接对象（供 gRPC 服务层设置其他通道使用）。
     */
    ProviderConnection getConnection(String providerId);
}
