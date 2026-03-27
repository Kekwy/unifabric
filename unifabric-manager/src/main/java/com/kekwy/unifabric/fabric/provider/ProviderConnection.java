package com.kekwy.unifabric.fabric.provider;

import com.kekwy.unifabric.proto.provider.DeploymentEnvelope;
import com.kekwy.unifabric.proto.provider.SignalingEnvelope;
import com.kekwy.unifabric.proto.provider.ControlEnvelope;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * 封装单个 Provider 的多通道连接。
 * <p>
 * 主要管理 DeploymentChannel 双向流，提供基于 {@code message_id}/{@code correlation_id}
 * 的请求-响应关联机制。SignalingChannel 和 ControlChannel 的发送端也在此持有。
 */
public class ProviderConnection {

    private static final Logger log = LoggerFactory.getLogger(ProviderConnection.class);
    private static final long DEFAULT_TIMEOUT_SECONDS = 60;

    private final String providerId;

    private volatile StreamObserver<DeploymentEnvelope> deploymentSender;
    private volatile StreamObserver<SignalingEnvelope> signalingSender;
    private volatile StreamObserver<ControlEnvelope> controlSender;

    /** 论文 3.2.2：各通道独立连通性，用于 Online / Degraded 判定 */
    private volatile boolean controlConnected;
    private volatile boolean deploymentConnected;
    private volatile boolean signalingConnected;

    private final ConcurrentHashMap<String, CompletableFuture<DeploymentEnvelope>> pendingDeployments =
            new ConcurrentHashMap<>();

    private volatile boolean closed = false;

    public ProviderConnection(String providerId) {
        this.providerId = providerId;
    }

    public String getProviderId() {
        return providerId;
    }

    public boolean isClosed() {
        return closed;
    }

    // ======================== Deployment Channel ========================

    public void setDeploymentSender(StreamObserver<DeploymentEnvelope> sender) {
        this.deploymentSender = sender;
    }

    public StreamObserver<DeploymentEnvelope> getDeploymentSender() {
        return deploymentSender;
    }

    /**
     * 仅当当前 deploymentSender 与 {@code senderThatClosed} 为同一引用时置空并返回 true，
     * 避免旧流晚于新流关闭时误清掉新连接。
     */
    public boolean clearDeploymentSenderIfEquals(StreamObserver<DeploymentEnvelope> senderThatClosed) {
        if (senderThatClosed == null) return false;
        if (this.deploymentSender == senderThatClosed) {
            this.deploymentSender = null;
            return true;
        }
        return false;
    }

    /**
     * 通过 DeploymentChannel 发送部署请求，返回异步结果。
     * <p>
     * message_id 由本方法自动填充，Provider 响应时在 correlation_id 中回传。
     */
    public CompletableFuture<DeploymentEnvelope> sendDeployment(DeploymentEnvelope.Builder envelopeBuilder) {
        return sendDeployment(envelopeBuilder, DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    public CompletableFuture<DeploymentEnvelope> sendDeployment(DeploymentEnvelope.Builder envelopeBuilder,
                                                                long timeout, TimeUnit unit) {
        if (closed) {
            return CompletableFuture.failedFuture(
                    new IllegalStateException("ProviderConnection 已关闭: " + providerId));
        }
        StreamObserver<DeploymentEnvelope> sender = this.deploymentSender;
        if (sender == null) {
            return CompletableFuture.failedFuture(
                    new IllegalStateException("DeploymentChannel 未建立: " + providerId));
        }

        String messageId = UUID.randomUUID().toString();
        DeploymentEnvelope envelope = envelopeBuilder.setMessageId(messageId).build();

        CompletableFuture<DeploymentEnvelope> future = new CompletableFuture<>();
        pendingDeployments.put(messageId, future);

        future.orTimeout(timeout, unit).whenComplete((resp, ex) -> {
            pendingDeployments.remove(messageId);
            if (ex != null) {
                log.warn("部署请求超时或异常: providerId={}, messageId={}", providerId, messageId);
            }
        });

        try {
            synchronized (sender) {
                sender.onNext(envelope);
            }
        } catch (Exception e) {
            pendingDeployments.remove(messageId);
            future.completeExceptionally(e);
        }

        return future;
    }

    /**
     * 处理从 Provider 回传的部署响应，通过 correlation_id 匹配并完成对应的 Future。
     */
    public void onDeploymentResponse(DeploymentEnvelope response) {
        String correlationId = response.getCorrelationId();
        CompletableFuture<DeploymentEnvelope> future = pendingDeployments.remove(correlationId);
        if (future != null) {
            future.complete(response);
        } else {
            log.warn("收到无法匹配的部署响应: providerId={}, correlationId={}", providerId, correlationId);
        }
    }

    // ======================== Signaling Channel ========================

    public void setSignalingSender(StreamObserver<SignalingEnvelope> sender) {
        this.signalingSender = sender;
    }

    public StreamObserver<SignalingEnvelope> getSignalingSender() {
        return signalingSender;
    }

    public void sendSignaling(SignalingEnvelope envelope) {
        StreamObserver<SignalingEnvelope> sender = this.signalingSender;
        if (sender == null) {
            log.warn("SignalingChannel 未建立: providerId={}", providerId);
            return;
        }
        synchronized (sender) {
            sender.onNext(envelope);
        }
    }

    // ======================== Control Channel ========================

    public void setControlSender(StreamObserver<ControlEnvelope> sender) {
        this.controlSender = sender;
    }

    public StreamObserver<ControlEnvelope> getControlSender() {
        return controlSender;
    }

    public void setControlConnected(boolean controlConnected) {
        this.controlConnected = controlConnected;
    }

    public boolean isControlConnected() {
        return controlConnected;
    }

    public void setDeploymentConnected(boolean deploymentConnected) {
        this.deploymentConnected = deploymentConnected;
    }

    public boolean isDeploymentConnected() {
        return deploymentConnected;
    }

    public void setSignalingConnected(boolean signalingConnected) {
        this.signalingConnected = signalingConnected;
    }

    public boolean isSignalingConnected() {
        return signalingConnected;
    }

    /** 三通道均已建立 */
    public boolean isFullyConnected() {
        return controlConnected && deploymentConnected && signalingConnected;
    }

    /** 至少一通道已建立且未全部就绪 */
    public boolean isPartiallyConnected() {
        return controlConnected || deploymentConnected || signalingConnected;
    }

    public void sendControl(ControlEnvelope envelope) {
        StreamObserver<ControlEnvelope> sender = this.controlSender;
        if (sender == null) {
            log.warn("ControlChannel 未建立: providerId={}", providerId);
            return;
        }
        synchronized (sender) {
            sender.onNext(envelope);
        }
    }

    // ======================== Lifecycle ========================

    /**
     * 关闭连接，取消所有待处理请求并关闭所有通道。
     */
    public void close() {
        if (closed) return;
        closed = true;
        controlConnected = false;
        deploymentConnected = false;
        signalingConnected = false;

        RuntimeException cause = new RuntimeException("ProviderConnection 已关闭: " + providerId);
        pendingDeployments.values().forEach(f -> f.completeExceptionally(cause));
        pendingDeployments.clear();

        closeStreamQuietly(deploymentSender, "deployment");
        closeStreamQuietly(signalingSender, "signaling");
        closeStreamQuietly(controlSender, "control");

        log.info("ProviderConnection 已关闭: providerId={}", providerId);
    }

    private void closeStreamQuietly(StreamObserver<?> observer, String channelName) {
        if (observer == null) return;
        try {
            synchronized (observer) {
                observer.onCompleted();
            }
        } catch (Exception e) {
            log.debug("关闭 {} channel 时出现异常: providerId={}", channelName, providerId, e);
        }
    }
}
