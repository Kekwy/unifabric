package com.kekwy.unifabric.adapter.deployment;

import com.kekwy.unifabric.adapter.artifact.ArtifactFetcher;
import com.kekwy.unifabric.adapter.config.AdapterIdentity;
import com.kekwy.unifabric.adapter.provider.ResourceProvider;
import com.kekwy.unifabric.adapter.registry.AdapterRegistryClient;
import com.kekwy.unifabric.adapter.signaling.SignalingService;
import com.kekwy.unifabric.adapter.registry.DelegatingObserver;
import com.kekwy.unifabric.adapter.util.ExponentialBackoff;
import com.kekwy.unifabric.proto.fabric.ProviderRegistryServiceGrpc;
import com.kekwy.unifabric.proto.provider.*;
import io.grpc.Metadata;
import io.grpc.stub.MetadataUtils;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 管理 DeploymentChannel 生命周期与部署指令执行。
 */
@Service
public class DeploymentService {

    private static final Logger log = LoggerFactory.getLogger(DeploymentService.class);
    private final ProviderRegistryServiceGrpc.ProviderRegistryServiceStub asyncStub;
    private final ScheduledExecutorService scheduler;
    private final ResourceProvider resourceProvider;
    private final ArtifactFetcher artifactFetcher;
    private final AdapterIdentity identity;
    private final SignalingService signalingService;

    private volatile boolean closed = false;
    private volatile StreamObserver<DeploymentEnvelope> deploymentSender;
    private final ExponentialBackoff reconnectBackoff = new ExponentialBackoff();

    public DeploymentService(ProviderRegistryServiceGrpc.ProviderRegistryServiceStub providerRegistryAsyncStub,
                             ScheduledExecutorService adapterScheduler,
                             ResourceProvider resourceProvider,
                             ArtifactFetcher artifactFetcher,
                             AdapterIdentity identity,
                             SignalingService signalingService) {
        this.asyncStub = providerRegistryAsyncStub;
        this.scheduler = adapterScheduler;
        this.resourceProvider = resourceProvider;
        this.artifactFetcher = artifactFetcher;
        this.identity = identity;
        this.signalingService = signalingService;
    }

    public void openChannel() {
        String providerId = identity != null ? identity.getProviderId() : null;
        if (closed || asyncStub == null || providerId == null) return;

        StreamObserver<DeploymentEnvelope> prev = this.deploymentSender;
        this.deploymentSender = null;
        if (prev != null) {
            try { prev.onCompleted(); } catch (Exception e) { log.trace("关闭旧 Deployment 流: {}", e.getMessage()); }
        }

        DelegatingObserver<DeploymentEnvelope> proxy = new DelegatingObserver<>();
        final StreamObserver<DeploymentEnvelope> thisSender = proxy;
        StreamObserver<DeploymentEnvelope> receiver = new DeploymentMessageDispatcher(
                this, signalingService, proxy, () -> onDisconnect(thisSender));

        Metadata headers = new Metadata();
        headers.put(AdapterRegistryClient.PROVIDER_ID_METADATA_KEY, providerId);
        var headerStub = asyncStub.withInterceptors(MetadataUtils.newAttachHeadersInterceptor(headers));

        StreamObserver<DeploymentEnvelope> sender = headerStub.deploymentChannel(receiver);
        proxy.setDelegate(sender);
        this.deploymentSender = proxy;
        reconnectBackoff.reset();

        log.info("DeploymentChannel 已建立: providerId={}", providerId);
    }

    private void onDisconnect(StreamObserver<DeploymentEnvelope> disconnectedSender) {
        if (this.deploymentSender != disconnectedSender) {
            log.debug("忽略旧 DeploymentChannel 的断开事件");
            return;
        }
        this.deploymentSender = null;
        if (closed) return;
        long delayMs = reconnectBackoff.nextDelayMs();
        log.warn("DeploymentChannel 断开，{}ms 后重连（指数退避）...", delayMs);
        scheduler.schedule(() -> {
            try { disconnectedSender.onCompleted(); } catch (Exception e) { log.trace("关闭旧流: {}", e.getMessage()); }
            openChannel();
        }, delayMs, TimeUnit.MILLISECONDS);
    }

    public void close() {
        closed = true;
    }

    public DeployInstanceResponse deployInstance(DeployInstanceRequest request) throws IOException {
        Path artifactPath = null;
        String artifactUrl = request.getArtifactUrl();
        if (artifactUrl != null && !artifactUrl.isBlank()) {
            artifactPath = artifactFetcher.fetch(request.getInstanceId(), artifactUrl);
        }
        return resourceProvider.deployInstance(request, artifactPath);
    }

    public StopInstanceResponse stopInstance(StopInstanceRequest request) {
        return resourceProvider.stopInstance(request.getInstanceId());
    }

    public RemoveInstanceResponse removeInstance(RemoveInstanceRequest request) {
        return resourceProvider.removeInstance(request.getInstanceId());
    }

    public GetInstanceStatusResponse getInstanceStatus(GetInstanceStatusRequest request) {
        return resourceProvider.getInstanceStatus(request.getInstanceId());
    }

    /** 供部署后补报端点：若响应中未带 {@link com.kekwy.unifabric.proto.provider.InstanceEndpoint} 再尝试解析 */
    public com.kekwy.unifabric.proto.provider.InstanceEndpoint tryGetInstanceEndpoint(String instanceId) {
        if (instanceId == null || instanceId.isBlank()) {
            return null;
        }
        return resourceProvider.getInstanceEndpoint(instanceId);
    }
}
