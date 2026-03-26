package com.kekwy.unifabric.adapter.deployment;

import com.kekwy.unifabric.adapter.actor.ActorRouter;
import com.kekwy.unifabric.adapter.artifact.ArtifactFetcher;
import com.kekwy.unifabric.adapter.artifact.ArtifactStore;
import com.kekwy.unifabric.adapter.signaling.SignalingService;
import com.kekwy.unifabric.adapter.config.AdapterIdentity;
import com.kekwy.unifabric.adapter.engine.ResourceEngine;
import com.kekwy.unifabric.adapter.registry.DelegatingObserver;
import com.kekwy.unifabric.adapter.registry.AdapterRegistryClient;
import com.kekwy.unifabric.proto.fabric.ProviderRegistryServiceGrpc;
import com.kekwy.unifabric.proto.provider.DeployActorRequest;
import com.kekwy.unifabric.proto.provider.DeployActorResponse;
import com.kekwy.unifabric.proto.provider.DeploymentEnvelope;
import com.kekwy.unifabric.proto.provider.GetActorStatusRequest;
import com.kekwy.unifabric.proto.provider.GetActorStatusResponse;
import com.kekwy.unifabric.proto.provider.RemoveActorRequest;
import com.kekwy.unifabric.proto.provider.RemoveActorResponse;
import com.kekwy.unifabric.proto.provider.DownstreamGroup;
import com.kekwy.unifabric.proto.provider.StopActorRequest;
import com.kekwy.unifabric.proto.provider.StopActorResponse;
import io.grpc.Metadata;
import io.grpc.stub.MetadataUtils;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 管理 DeploymentChannel 生命周期；asyncStub、scheduler 及业务依赖由 Spring 构造注入，
 * providerId 从 {@link AdapterIdentity} 读取。
 */
@Service
public class DeploymentService {

    private static final Logger log = LoggerFactory.getLogger(DeploymentService.class);
    private static final long RECONNECT_DELAY_SECONDS = 5;

    private final ProviderRegistryServiceGrpc.ProviderRegistryServiceStub asyncStub;
    private final ScheduledExecutorService scheduler;
    private final ResourceEngine engine;
    private final ArtifactFetcher artifactFetcher;
    private final ArtifactStore artifactStore;
    private final ActorRouter actorRouter;
    private final AdapterIdentity identity;

    private volatile boolean closed = false;
    /** 当前 Deployment 流发送端，重连前需 onCompleted 关闭，避免多流并存 */
    private volatile StreamObserver<DeploymentEnvelope> deploymentSender;

    public DeploymentService(ProviderRegistryServiceGrpc.ProviderRegistryServiceStub providerRegistryAsyncStub,
                             ScheduledExecutorService adapterScheduler,
                             ResourceEngine engine,
                             ArtifactFetcher artifactFetcher,
                             ArtifactStore artifactStore,
                             ActorRouter actorRouter,
                             AdapterIdentity identity) {
        this.asyncStub = providerRegistryAsyncStub;
        this.scheduler = adapterScheduler;
        this.engine = engine;
        this.artifactFetcher = artifactFetcher;
        this.artifactStore = artifactStore;
        this.actorRouter = actorRouter;
        this.identity = identity;
    }

    /** 建立 Deployment 流（providerId 从 AdapterIdentity 读取，重连时同样）。重连前先关闭旧流，避免多流并存。 */
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
        StreamObserver<DeploymentEnvelope> receiver = new DeploymentMessageDispatcher(this, proxy,
                () -> onDisconnect(thisSender));

        Metadata headers = new Metadata();
        headers.put(AdapterRegistryClient.PROVIDER_ID_METADATA_KEY, providerId);
        var headerStub = asyncStub.withInterceptors(MetadataUtils.newAttachHeadersInterceptor(headers));

        StreamObserver<DeploymentEnvelope> sender = headerStub.deploymentChannel(receiver);
        proxy.setDelegate(sender);
        this.deploymentSender = proxy;

        log.info("DeploymentChannel 已建立: providerId={}", providerId);
    }

    private void onDisconnect(StreamObserver<DeploymentEnvelope> disconnectedSender) {
        if (this.deploymentSender != disconnectedSender) {
            log.debug("忽略旧 DeploymentChannel 的断开事件");
            return;
        }
        this.deploymentSender = null;
        if (closed) return;
        log.warn("DeploymentChannel 断开，{}s 后重连...", RECONNECT_DELAY_SECONDS);
        scheduler.schedule(() -> {
            try { disconnectedSender.onCompleted(); } catch (Exception e) { log.trace("关闭旧流: {}", e.getMessage()); }
            openChannel();
        }, RECONNECT_DELAY_SECONDS, TimeUnit.SECONDS);
    }

    public void close() {
        closed = true;
    }

    // --- 业务方法，供 DeploymentMessageDispatcher 委托调用 ---

    @SuppressWarnings("ConstantValue")
    public DeployActorResponse deployActor(DeployActorRequest request) throws IOException {
        Path artifactPath = null;
        String artifactUrl = request.getArtifactUrl();
        if (artifactUrl != null && !artifactUrl.isBlank()) {
            artifactPath = artifactFetcher.fetch(request.getActorId(), artifactUrl);
        }
        String actorId = request.getActorId();
        actorRouter.registerActorRouting(actorId, request.getInstanceIndex(),
                request.getUpstreamActorAddrsList(), request.getDownstreamGroupsList());

        Map<Integer, Path> conditionFunctionPaths = new HashMap<>();
        for (DownstreamGroup group : request.getDownstreamGroupsList()) {
            if (!group.hasConditionFunction()) {
                continue;
            }
            int port = group.getOutputPort();
            Path path = artifactStore.storeConditionFunction(actorId, port, group.getConditionFunction().toByteArray());
            conditionFunctionPaths.put(port, path);
        }

        return engine.deployActor(request, artifactPath, conditionFunctionPaths);
    }

    public StopActorResponse stopActor(StopActorRequest request) {
        return engine.stopActor(request.getActorId());
    }

    public RemoveActorResponse removeActor(RemoveActorRequest request) {
        return engine.removeActor(request.getActorId());
    }

    public GetActorStatusResponse getActorStatus(GetActorStatusRequest request) {
        return engine.getActorStatus(request.getActorId());
    }
}
