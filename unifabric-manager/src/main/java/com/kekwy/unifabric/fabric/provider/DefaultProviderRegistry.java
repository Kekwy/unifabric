package com.kekwy.unifabric.fabric.provider;

import com.kekwy.unifabric.fabric.discovery.NodeDiscoveryManager;
import com.kekwy.unifabric.proto.common.ResourceCapacity;
import com.kekwy.unifabric.proto.provider.ControlEnvelope;
import com.kekwy.unifabric.proto.provider.DeploymentEnvelope;
import com.kekwy.unifabric.proto.provider.RegisterProviderRequest;
import com.kekwy.unifabric.proto.provider.SignalingEnvelope;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.kekwy.unifabric.fabric.scheduling.SchedulingTopologyChangedEvent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;

/**
 * {@link ProviderRegistry} 的默认实现。
 * <p>
 * 维护 Provider 元数据、各通道连通性（论文 3.2.2），并定期扫描心跳超时。
 */
@Service
public class DefaultProviderRegistry implements ProviderRegistry {

    private static final Logger log = LoggerFactory.getLogger(DefaultProviderRegistry.class);
    private static final Duration HEARTBEAT_TIMEOUT = Duration.ofSeconds(90);

    private final ConcurrentHashMap<String, ProviderInfo> providers = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ProviderConnection> connections = new ConcurrentHashMap<>();
    private final ScheduledExecutorService watchdog;

    private volatile NodeDiscoveryManager nodeDiscoveryManager;
    private volatile InstanceRegistry instanceRegistry;

    @Autowired(required = false)
    private ApplicationEventPublisher eventPublisher;

    public DefaultProviderRegistry() {
        watchdog = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "provider-watchdog");
            t.setDaemon(true);
            return t;
        });
        watchdog.scheduleAtFixedRate(this::checkHeartbeats, 30, 30, TimeUnit.SECONDS);
    }

    @Autowired(required = false)
    @Lazy
    public void setNodeDiscoveryManager(NodeDiscoveryManager nodeDiscoveryManager) {
        this.nodeDiscoveryManager = nodeDiscoveryManager;
    }

    @Autowired(required = false)
    @Lazy
    public void setInstanceRegistry(InstanceRegistry instanceRegistry) {
        this.instanceRegistry = instanceRegistry;
    }

    @PreDestroy
    public void shutdown() {
        watchdog.shutdownNow();
        connections.values().forEach(ProviderConnection::close);
    }

    // ======================== 注册/注销 ========================

    @Override
    public String register(RegisterProviderRequest request) {
        String providerId = UUID.randomUUID().toString();
        ProviderInfo info = new ProviderInfo(
                providerId,
                request.getProviderName(),
                request.getDescription(),
                request.getProviderType(),
                request.getZone(),
                request.getTagsList()
        );
        providers.put(providerId, info);
        connections.put(providerId, new ProviderConnection(providerId));
        log.info("Provider 注册成功（Connecting）: {}", info);
        notifyDomainViewRefresh();
        publishSchedulingTopologyChanged();
        return providerId;
    }

    @Override
    public void deregister(String providerId) {
        ProviderInfo removed = providers.remove(providerId);
        if (removed != null) {
            removed.setStatus(ProviderInfo.Status.OFFLINE);
            log.info("Provider 已注销: {}", removed);
        }
        ProviderConnection conn = connections.remove(providerId);
        if (conn != null) {
            conn.close();
        }
        notifyDomainViewRefresh();
        publishSchedulingTopologyChanged();
    }

    // ======================== 心跳与标签 ========================

    @Override
    public void heartbeat(String providerId) {
        ProviderInfo info = providers.get(providerId);
        if (info == null) {
            log.warn("收到未知 Provider 的心跳: providerId={}", providerId);
            return;
        }
        info.touchHeartbeat();
        if (info.getStatus() == ProviderInfo.Status.OFFLINE) {
            info.setStatus(ProviderInfo.Status.RECONNECTING);
            log.info("Provider 心跳恢复（Reconnecting）: {}", info);
        }
        updateStatusFromChannels(providerId);
        notifyDomainViewRefresh();
    }

    @Override
    public void updateTags(String providerId, List<String> tags) {
        ProviderInfo info = providers.get(providerId);
        if (info == null) {
            return;
        }
        info.updateTagsFromHeartbeat(tags);
        notifyDomainViewRefresh();
    }

    @Override
    public void updateResourceCapacity(String providerId, ResourceCapacity capacity) {
        ProviderInfo info = providers.get(providerId);
        if (info == null) {
            log.warn("收到未知 Provider 的资源上报: providerId={}", providerId);
            return;
        }
        info.touchResourceReport(capacity);
        log.debug("已更新 Provider 资源视图: providerId={}", providerId);
        notifyDomainViewRefresh();
    }

    // ======================== ControlChannel ========================

    @Override
    public void openControlChannel(String providerId, StreamObserver<ControlEnvelope> controlSender) {
        ProviderInfo info = providers.get(providerId);
        if (info == null) {
            log.warn("未注册的 Provider 尝试打开 ControlChannel: providerId={}", providerId);
            if (controlSender != null) {
                controlSender.onError(
                        io.grpc.Status.NOT_FOUND
                                .withDescription("Provider 未注册: " + providerId)
                                .asRuntimeException());
            }
            return;
        }
        ProviderConnection conn = connections.computeIfAbsent(providerId, ProviderConnection::new);
        StreamObserver<ControlEnvelope> old = conn.getControlSender();
        if (old != null) {
            try {
                old.onCompleted();
            } catch (Exception e) {
                log.trace("关闭旧 Control 流异常: {}", e.getMessage());
            }
        }
        conn.setControlSender(controlSender);
        conn.setControlConnected(true);
        log.info("ControlChannel 已建立: providerId={}, name={}", providerId, info.getName());
        updateStatusFromChannels(providerId);
        notifyDomainViewRefresh();
    }

    @Override
    public void closeControlChannel(String providerId, StreamObserver<ControlEnvelope> senderThatClosed) {
        ProviderConnection conn = connections.get(providerId);
        if (conn == null) {
            return;
        }
        if (senderThatClosed != null && conn.getControlSender() == senderThatClosed) {
            conn.setControlSender(null);
        }
        conn.setControlConnected(false);
        ProviderInfo info = providers.get(providerId);
        if (info != null) {
            if (info.getStatus() != ProviderInfo.Status.OFFLINE) {
                info.setStatus(ProviderInfo.Status.RECONNECTING);
                log.info("ControlChannel 已断开，标记 Reconnecting: providerId={}, name={}",
                        providerId, info.getName());
            }
            updateStatusFromChannels(providerId);
        }
        notifyDomainViewRefresh();
    }

    // ======================== SignalingChannel ========================

    @Override
    public void openSignalingChannel(String providerId, StreamObserver<SignalingEnvelope> signalingSender) {
        ProviderInfo info = providers.get(providerId);
        if (info == null) {
            log.warn("未注册的 Provider 尝试打开 SignalingChannel: providerId={}", providerId);
            if (signalingSender != null) {
                signalingSender.onError(
                        io.grpc.Status.NOT_FOUND
                                .withDescription("Provider 未注册: " + providerId)
                                .asRuntimeException());
            }
            return;
        }
        ProviderConnection conn = connections.computeIfAbsent(providerId, ProviderConnection::new);
        StreamObserver<SignalingEnvelope> old = conn.getSignalingSender();
        if (old != null) {
            try {
                old.onCompleted();
            } catch (Exception e) {
                log.trace("关闭旧 Signaling 流: {}", e.getMessage());
            }
        }
        conn.setSignalingSender(signalingSender);
        conn.setSignalingConnected(true);
        log.info("SignalingChannel 已建立: providerId={}", providerId);
        updateStatusFromChannels(providerId);
        notifyDomainViewRefresh();
    }

    @Override
    public void closeSignalingChannel(String providerId, StreamObserver<SignalingEnvelope> senderThatClosed) {
        ProviderConnection conn = connections.get(providerId);
        if (conn == null) {
            return;
        }
        if (senderThatClosed != null && conn.getSignalingSender() == senderThatClosed) {
            conn.setSignalingSender(null);
        }
        conn.setSignalingConnected(false);
        ProviderInfo info = providers.get(providerId);
        if (info != null) {
            log.info("SignalingChannel 已断开: providerId={}, name={}", providerId, info.getName());
            applyDegradedOrOffline(providerId, conn, info);
        }
        notifyDomainViewRefresh();
    }

    // ======================== DeploymentChannel ========================

    @Override
    public void openDeploymentChannel(String providerId, StreamObserver<DeploymentEnvelope> deploymentSender) {
        ProviderInfo info = providers.get(providerId);
        if (info == null) {
            log.warn("未注册的 Provider 尝试打开 DeploymentChannel: providerId={}", providerId);
            deploymentSender.onError(
                    io.grpc.Status.NOT_FOUND
                            .withDescription("Provider 未注册: " + providerId)
                            .asRuntimeException());
            return;
        }

        ProviderConnection conn = connections.get(providerId);
        if (conn == null) {
            conn = new ProviderConnection(providerId);
            connections.put(providerId, conn);
        }

        StreamObserver<DeploymentEnvelope> oldSender = conn.getDeploymentSender();
        if (oldSender != null) {
            log.info("替换 Provider 已有的 DeploymentChannel，先关闭旧流: providerId={}", providerId);
            try {
                oldSender.onCompleted();
            } catch (Exception e) {
                log.trace("关闭旧 Deployment 流异常: {}", e.getMessage());
            }
        }

        conn.setDeploymentSender(deploymentSender);
        conn.setDeploymentConnected(true);
        log.info("DeploymentChannel 已建立: providerId={}, name={}", providerId, info.getName());
        updateStatusFromChannels(providerId);
        notifyDomainViewRefresh();
    }

    @Override
    public void handleDeploymentResponse(String providerId, DeploymentEnvelope response) {
        ProviderConnection conn = connections.get(providerId);
        if (conn != null) {
            conn.onDeploymentResponse(response);
        } else {
            log.warn("收到部署响应但无活跃连接: providerId={}, correlationId={}",
                    providerId, response.getCorrelationId());
        }
    }

    @Override
    public void closeDeploymentChannel(String providerId, StreamObserver<DeploymentEnvelope> senderThatClosed) {
        ProviderConnection conn = connections.get(providerId);
        if (conn != null && conn.clearDeploymentSenderIfEquals(senderThatClosed)) {
            conn.setDeploymentConnected(false);
            ProviderInfo info = providers.get(providerId);
            if (info != null) {
                log.info("DeploymentChannel 已断开: providerId={}, name={}", providerId, info.getName());
                applyDegradedOrOffline(providerId, conn, info);
            }
        }
        notifyDomainViewRefresh();
    }

    /**
     * 部署或信令通道断开：若控制通道仍视为连通且心跳路径仍有效，则 Degraded；否则 Offline。
     */
    private void applyDegradedOrOffline(String providerId, ProviderConnection conn, ProviderInfo info) {
        if (conn.isControlConnected() && info.getStatus() != ProviderInfo.Status.OFFLINE) {
            info.setStatus(ProviderInfo.Status.DEGRADED);
        } else if (!conn.isControlConnected()) {
            info.setStatus(ProviderInfo.Status.OFFLINE);
        } else {
            updateStatusFromChannels(providerId);
        }
    }

    /**
     * 根据三通道连通性刷新状态（在心跳仍有效的前提下）。
     */
    private void updateStatusFromChannels(String providerId) {
        ProviderInfo info = providers.get(providerId);
        ProviderConnection conn = connections.get(providerId);
        if (info == null || conn == null) {
            return;
        }
        if (info.getStatus() == ProviderInfo.Status.OFFLINE) {
            return;
        }
        if (conn.isFullyConnected()) {
            info.setStatus(ProviderInfo.Status.ONLINE);
            return;
        }
        if (conn.isControlConnected()) {
            if (info.getStatus() == ProviderInfo.Status.CONNECTING
                    || info.getStatus() == ProviderInfo.Status.RECONNECTING) {
                // 仍在建连或重连中，仅部分通道就绪
            }
            if (!conn.isFullyConnected() && conn.isPartiallyConnected()) {
                info.setStatus(ProviderInfo.Status.DEGRADED);
            } else if (!conn.isPartiallyConnected()) {
                info.setStatus(ProviderInfo.Status.CONNECTING);
            }
        }
    }

    @Override
    public CompletableFuture<DeploymentEnvelope> sendDeployment(String providerId,
                                                                 DeploymentEnvelope.Builder envelopeBuilder) {
        ProviderConnection conn = connections.get(providerId);
        ProviderInfo info = providers.get(providerId);
        if (conn == null || conn.isClosed()) {
            return CompletableFuture.failedFuture(
                    new IllegalStateException("Provider 无活跃连接: " + providerId));
        }
        if (info != null && info.getStatus() == ProviderInfo.Status.OFFLINE) {
            return CompletableFuture.failedFuture(
                    new IllegalStateException("Provider 已 Offline: " + providerId));
        }
        if (info != null && info.getStatus() == ProviderInfo.Status.DEGRADED) {
            return CompletableFuture.failedFuture(
                    new IllegalStateException("Provider 处于 Degraded，暂停新部署: " + providerId));
        }
        if (info != null && info.getStatus() != ProviderInfo.Status.ONLINE) {
            return CompletableFuture.failedFuture(
                    new IllegalStateException("Provider 非 Online，无法部署: " + providerId + " status=" + info.getStatus()));
        }
        if (!conn.isDeploymentConnected() || conn.getDeploymentSender() == null) {
            return CompletableFuture.failedFuture(
                    new IllegalStateException("DeploymentChannel 未就绪: " + providerId));
        }
        if (envelopeBuilder.getMessageCase() == DeploymentEnvelope.MessageCase.DEPLOY_INSTANCE_REQUEST) {
            String iid = envelopeBuilder.getDeployInstanceRequest().getInstanceId();
            InstanceRegistry reg = instanceRegistry;
            if (reg != null && iid != null && !iid.isBlank()) {
                reg.trackInstance(iid, providerId);
            }
        }
        return conn.sendDeployment(envelopeBuilder);
    }

    // ======================== 查询 ========================

    @Override
    public ProviderInfo getProvider(String providerId) {
        return providers.get(providerId);
    }

    @Override
    public List<ProviderInfo> listProviders() {
        return List.copyOf(providers.values());
    }

    @Override
    public List<ProviderInfo> listOnlineProviders() {
        return providers.values().stream()
                .filter(p -> p.getStatus() == ProviderInfo.Status.ONLINE)
                .filter(p -> {
                    ProviderConnection c = connections.get(p.getProviderId());
                    return c != null && !c.isClosed()
                            && c.getDeploymentSender() != null
                            && c.isDeploymentConnected();
                })
                .toList();
    }

    @Override
    public List<ProviderInfo> listOnlineProvidersByType(String providerType) {
        return listOnlineProviders().stream()
                .filter(p -> p.getProviderType().equals(providerType))
                .toList();
    }

    @Override
    public ProviderConnection getConnection(String providerId) {
        return connections.get(providerId);
    }

    // ======================== 心跳超时检测 ========================

    private void checkHeartbeats() {
        Instant threshold = Instant.now().minus(HEARTBEAT_TIMEOUT);
        for (ProviderInfo info : providers.values()) {
            if (info.getLastHeartbeat().isBefore(threshold)) {
                if (info.getStatus() != ProviderInfo.Status.OFFLINE) {
                    log.warn("Provider 心跳超时，标记为 Offline: {}", info);
                    info.setStatus(ProviderInfo.Status.OFFLINE);
                    notifyDomainViewRefresh();
                }
            }
        }
    }

    private void notifyDomainViewRefresh() {
        NodeDiscoveryManager ndm = nodeDiscoveryManager;
        if (ndm != null) {
            ndm.refreshFromProviderRegistry(this);
        }
    }

    private void publishSchedulingTopologyChanged() {
        ApplicationEventPublisher publisher = eventPublisher;
        if (publisher != null) {
            publisher.publishEvent(new SchedulingTopologyChangedEvent(this));
        }
    }
}
