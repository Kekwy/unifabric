package com.kekwy.unifabric.fabric.provider;

import com.kekwy.unifabric.proto.provider.DeploymentEnvelope;
import com.kekwy.unifabric.proto.provider.RegisterProviderRequest;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
 * 维护 Provider 元数据、通道连接，
 * 并定期扫描心跳超时的 Provider 将其标记为 OFFLINE。
 */
@Service
public class DefaultProviderRegistry implements ProviderRegistry {

    private static final Logger log = LoggerFactory.getLogger(DefaultProviderRegistry.class);
    private static final Duration HEARTBEAT_TIMEOUT = Duration.ofSeconds(90);

    private final ConcurrentHashMap<String, ProviderInfo> providers = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ProviderConnection> connections = new ConcurrentHashMap<>();
    private final ScheduledExecutorService watchdog;

    public DefaultProviderRegistry() {
        watchdog = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "provider-watchdog");
            t.setDaemon(true);
            return t;
        });
        watchdog.scheduleAtFixedRate(this::checkHeartbeats, 30, 30, TimeUnit.SECONDS);
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
        log.info("Provider 注册成功: {}", info);
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
    }

    // ======================== 心跳 ========================

    @Override
    public void heartbeat(String providerId) {
        ProviderInfo info = providers.get(providerId);
        if (info == null) {
            log.warn("收到未知 Provider 的心跳: providerId={}", providerId);
            return;
        }
        info.touchHeartbeat();
        if (info.getStatus() == ProviderInfo.Status.OFFLINE) {
            info.setStatus(ProviderInfo.Status.ONLINE);
            log.info("Provider 重新上线: {}", info);
        }
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
        info.setStatus(ProviderInfo.Status.ONLINE);
        log.info("DeploymentChannel 已建立: providerId={}, name={}", providerId, info.getName());
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
            ProviderInfo info = providers.get(providerId);
            if (info != null) {
                info.setStatus(ProviderInfo.Status.OFFLINE);
                log.info("DeploymentChannel 已断开: providerId={}, name={}", providerId, info.getName());
            }
        }
    }

    // ======================== 部署命令发送 ========================

    @Override
    public CompletableFuture<DeploymentEnvelope> sendDeployment(String providerId,
                                                                 DeploymentEnvelope.Builder envelopeBuilder) {
        ProviderConnection conn = connections.get(providerId);
        if (conn == null || conn.isClosed()) {
            return CompletableFuture.failedFuture(
                    new IllegalStateException("Provider 无活跃连接: " + providerId));
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
                    ProviderConnection conn = connections.get(p.getProviderId());
                    return conn != null && !conn.isClosed() && conn.getDeploymentSender() != null;
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
            if (info.getStatus() == ProviderInfo.Status.ONLINE
                    && info.getLastHeartbeat().isBefore(threshold)) {
                log.warn("Provider 心跳超时，标记为 OFFLINE: {}", info);
                info.setStatus(ProviderInfo.Status.OFFLINE);
            }
        }
    }
}
