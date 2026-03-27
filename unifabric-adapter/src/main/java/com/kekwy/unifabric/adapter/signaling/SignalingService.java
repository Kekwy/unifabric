package com.kekwy.unifabric.adapter.signaling;

import com.kekwy.unifabric.adapter.config.AdapterIdentity;
import com.kekwy.unifabric.adapter.config.AdapterProperties;
import com.kekwy.unifabric.adapter.network.TcpProxyServer;
import com.kekwy.unifabric.adapter.registry.AdapterRegistryClient;
import com.kekwy.unifabric.adapter.registry.DelegatingObserver;
import com.kekwy.unifabric.adapter.util.ExponentialBackoff;
import com.kekwy.unifabric.proto.fabric.ProviderRegistryServiceGrpc;
import com.kekwy.unifabric.proto.provider.*;
import io.grpc.Metadata;
import io.grpc.stub.MetadataUtils;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.net.Socket;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 管理 SignalingChannel：跨 Provider 信令、实例端点上报与连通性协商（论文 3.5）。
 */
@Service
public class SignalingService {

    private static final Logger log = LoggerFactory.getLogger(SignalingService.class);

    private final ProviderRegistryServiceGrpc.ProviderRegistryServiceStub asyncStub;
    private final ScheduledExecutorService scheduler;
    private final AdapterIdentity identity;
    private final AdapterProperties adapterProperties;
    private final TcpProxyServer tcpProxyServer;
    private final ExecutorService signalingIoExecutor;

    private volatile boolean closed = false;
    private volatile StreamObserver<SignalingEnvelope> signalingSender;
    private final Object senderLock = new Object();
    private final ConcurrentLinkedQueue<SignalingEnvelope> pendingReports = new ConcurrentLinkedQueue<>();
    private final ExponentialBackoff reconnectBackoff = new ExponentialBackoff();

    /** 发起方等待对端 {@link CandidateUpdate}（论文 3.5.4） */
    private final ConcurrentHashMap<String, ConnectInstruction> pendingInitiatorByConnectId =
            new ConcurrentHashMap<>();

    public SignalingService(ProviderRegistryServiceGrpc.ProviderRegistryServiceStub providerRegistryAsyncStub,
                            ScheduledExecutorService adapterScheduler,
                            AdapterIdentity identity,
                            AdapterProperties adapterProperties,
                            TcpProxyServer tcpProxyServer,
                            @Qualifier("signalingIoExecutor") ExecutorService signalingIoExecutor) {
        this.asyncStub = providerRegistryAsyncStub;
        this.scheduler = adapterScheduler;
        this.identity = identity;
        this.adapterProperties = adapterProperties;
        this.tcpProxyServer = tcpProxyServer;
        this.signalingIoExecutor = signalingIoExecutor;
    }

    public void openChannel() {
        String providerId = identity != null ? identity.getProviderId() : null;
        if (closed || asyncStub == null || providerId == null) return;

        StreamObserver<SignalingEnvelope> prev = this.signalingSender;
        this.signalingSender = null;
        if (prev != null) {
            try {
                prev.onCompleted();
            } catch (Exception e) {
                log.debug("关闭旧 Signaling 流: {}", e.getMessage());
            }
        }

        DelegatingObserver<SignalingEnvelope> proxy = new DelegatingObserver<>();
        final StreamObserver<SignalingEnvelope> thisSender = proxy;
        StreamObserver<SignalingEnvelope> receiver = new SignalingMessageDispatcher(this,
                () -> onDisconnect(thisSender));

        Metadata headers = new Metadata();
        headers.put(AdapterRegistryClient.PROVIDER_ID_METADATA_KEY, providerId);
        var headerStub = asyncStub.withInterceptors(MetadataUtils.newAttachHeadersInterceptor(headers));

        StreamObserver<SignalingEnvelope> sender = headerStub.signalingChannel(receiver);
        proxy.setDelegate(sender);
        this.signalingSender = proxy;
        reconnectBackoff.reset();

        log.info("SignalingChannel 已建立: providerId={}", providerId);
        flushPendingReports();
    }

    private void onDisconnect(StreamObserver<SignalingEnvelope> disconnectedSender) {
        if (this.signalingSender != disconnectedSender) {
            log.debug("忽略旧 SignalingChannel 的断开事件");
            return;
        }
        this.signalingSender = null;
        if (closed) return;
        long delayMs = reconnectBackoff.nextDelayMs();
        log.warn("SignalingChannel 断开，{}ms 后重连（指数退避）...", delayMs);
        scheduler.schedule(() -> {
            try { disconnectedSender.onCompleted(); } catch (Exception e) { log.trace("关闭旧流: {}", e.getMessage()); }
            openChannel();
        }, delayMs, TimeUnit.MILLISECONDS);
    }

    private void flushPendingReports() {
        StreamObserver<SignalingEnvelope> sender = this.signalingSender;
        if (sender == null) return;
        SignalingEnvelope envelope;
        int count = 0;
        synchronized (senderLock) {
            while ((envelope = pendingReports.poll()) != null) {
                try {
                    sender.onNext(envelope);
                    count++;
                } catch (Exception e) {
                    log.warn("重放暂存信令失败，已丢弃剩余 {} 条", pendingReports.size() + 1, e);
                    break;
                }
            }
        }
        if (count > 0) {
            log.info("SignalingChannel 已重放 {} 条暂存上报", count);
        }
    }

    public void close() {
        closed = true;
    }

    public void handleConnectInstruction(ConnectInstruction instruction) {
        if (instruction == null) {
            return;
        }
        String cid = instruction.getConnectId();
        if (cid == null || cid.isBlank()) {
            return;
        }
        signalingIoExecutor.execute(() -> processConnectInstruction(instruction));
    }

    private void processConnectInstruction(ConnectInstruction ins) {
        try {
            String relayHost = ins.getRelayHost();
            int relayPort = ins.getRelayPort();
            boolean useRelay = relayHost != null && !relayHost.isBlank() && relayPort > 0;
            if (useRelay) {
                Socket relaySocket = new Socket(relayHost, relayPort);
                int localPort = tcpProxyServer.attachLocalAcceptToUpstream(relaySocket);
                emitCandidateUpdate(ins.getConnectId(), localPort, relayHost, relayPort);
                log.info("已建立经中继的本地代理: connectId={}, listenPort={}", ins.getConnectId(), localPort);
                return;
            }
            if (!ins.getInitiator()) {
                String dst = ins.getDstInstanceAddr();
                HostPort hp = parseHostPort(dst);
                if (hp == null) {
                    log.warn("ConnectInstruction 缺少有效 dst_instance_addr: connectId={}", ins.getConnectId());
                    return;
                }
                int localPort = tcpProxyServer.openInboundProxy(hp.host(), hp.port());
                emitCandidateUpdate(ins.getConnectId(), localPort, null, 0);
                log.info("应答方本地代理已监听: connectId={}, -> {}:{}, listenPort={}",
                        ins.getConnectId(), hp.host(), hp.port(), localPort);
                return;
            }
            pendingInitiatorByConnectId.put(ins.getConnectId(), ins);
            log.debug("发起方已登记，等待对端候选地址: connectId={}", ins.getConnectId());
        } catch (Exception e) {
            log.warn("处理 ConnectInstruction 失败: connectId={}, {}", ins.getConnectId(), e.getMessage());
        }
    }

    public void handleCandidateUpdate(CandidateUpdate update) {
        if (update == null) {
            return;
        }
        String cid = update.getConnectId();
        if (cid == null || cid.isBlank()) {
            return;
        }
        ConnectInstruction pending = pendingInitiatorByConnectId.get(cid);
        if (pending == null || !pending.getInitiator()) {
            return;
        }
        for (NetworkCandidate c : update.getCandidatesList()) {
            if (c.getType() != NetworkCandidate.CandidateType.DIRECT) {
                continue;
            }
            if (c.getPort() <= 0 || c.getHost() == null || c.getHost().isBlank()) {
                continue;
            }
            signalingIoExecutor.execute(() -> {
                try {
                    int localPort = tcpProxyServer.openInboundProxy(c.getHost(), c.getPort());
                    emitCandidateUpdate(cid, localPort, null, 0);
                    pendingInitiatorByConnectId.remove(cid);
                    log.info("发起方已根据对端候选建立代理: connectId={}, -> {}:{}, listenPort={}",
                            cid, c.getHost(), c.getPort(), localPort);
                } catch (Exception e) {
                    log.warn("发起方代理建立失败: connectId={}, {}", cid, e.getMessage());
                }
            });
            break;
        }
    }

    public void handleIceEnvelope(IceEnvelope iceEnvelope) {
        log.debug("收到 IceEnvelope: connectId={}", iceEnvelope != null ? iceEnvelope.getConnectId() : null);
    }

    public void handleResolveEndpointResponse(ResolveEndpointResponse response) {
        if (response == null) {
            return;
        }
        if (response.getErrorMessage() != null && !response.getErrorMessage().isBlank()) {
            log.warn("端点解析失败: correlationId={}, {}", response.getCorrelationId(), response.getErrorMessage());
            return;
        }
        String tier = response.getConnectivityTier();
        String cid = response.getConnectId();
        if (response.hasProxyEndpoint()) {
            InstanceEndpoint p = response.getProxyEndpoint();
            log.info("端点解析结果: tier={}, connectId={}, proxy={}:{}",
                    tier, cid, p.getHost(), p.getPort());
        } else {
            log.info("端点解析结果: tier={}, connectId={}, 无 proxy 字段", tier, cid);
        }
    }

    private void emitCandidateUpdate(String connectId, int directListenPort, String relayHost, int relayPort) {
        String host = advertiseHost();
        CandidateUpdate.Builder cb = CandidateUpdate.newBuilder().setConnectId(connectId);
        cb.addCandidates(NetworkCandidate.newBuilder()
                .setType(NetworkCandidate.CandidateType.DIRECT)
                .setHost(host)
                .setPort(directListenPort)
                .setPriority(100)
                .build());
        if (relayHost != null && !relayHost.isBlank() && relayPort > 0) {
            cb.addCandidates(NetworkCandidate.newBuilder()
                    .setType(NetworkCandidate.CandidateType.RELAY)
                    .setHost(relayHost)
                    .setPort(relayPort)
                    .setPriority(50)
                    .build());
        }
        SignalingEnvelope msg = SignalingEnvelope.newBuilder()
                .setTimestampMs(System.currentTimeMillis())
                .setCandidateUpdate(cb.build())
                .build();
        sendOrEnqueue(msg, "CandidateUpdate", connectId);
    }

    private String advertiseHost() {
        if (adapterProperties != null && adapterProperties.getHost() != null
                && !adapterProperties.getHost().isBlank()) {
            return adapterProperties.getHost();
        }
        return "127.0.0.1";
    }

    private record HostPort(String host, int port) {}

    private static HostPort parseHostPort(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        int colon = s.lastIndexOf(':');
        if (colon <= 0 || colon >= s.length() - 1) {
            return null;
        }
        String h = s.substring(0, colon).trim();
        String p = s.substring(colon + 1).trim();
        try {
            int port = Integer.parseInt(p);
            if (port <= 0 || port > 65535 || h.isEmpty()) {
                return null;
            }
            return new HostPort(h, port);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public void reportInstanceEndpoint(String instanceId, InstanceEndpoint endpoint) {
        if (instanceId == null || instanceId.isBlank() || endpoint == null) {
            return;
        }
        SignalingEnvelope msg = SignalingEnvelope.newBuilder()
                .setTimestampMs(System.currentTimeMillis())
                .setInstanceEndpointReport(InstanceEndpointReport.newBuilder()
                        .setInstanceId(instanceId)
                        .setEndpoint(endpoint)
                        .build())
                .build();
        if (!sendOrEnqueue(msg, "InstanceEndpointReport", instanceId)) {
            log.debug("SignalingChannel 未就绪，InstanceEndpointReport 已入队: instanceId={}", instanceId);
        }
    }

    /**
     * 上报容器级实例状态变更（通道未就绪时入队，重连后重放）。
     */
    public void reportInstanceStatusChanged(String instanceId,
                                           InstanceStatus previous,
                                           InstanceStatus current,
                                           String message) {
        SignalingEnvelope msg = SignalingEnvelope.newBuilder()
                .setTimestampMs(System.currentTimeMillis())
                .setInstanceStatusChanged(InstanceStatusChanged.newBuilder()
                        .setInstanceId(instanceId != null ? instanceId : "")
                        .setPreviousStatus(previous != null ? previous : InstanceStatus.INSTANCE_STATUS_UNSPECIFIED)
                        .setCurrentStatus(current != null ? current : InstanceStatus.INSTANCE_STATUS_UNSPECIFIED)
                        .setMessage(message != null ? message : "")
                        .build())
                .build();
        if (!sendOrEnqueue(msg, "InstanceStatusChanged", instanceId)) {
            log.debug("SignalingChannel 未就绪，InstanceStatusChanged 已入队: instanceId={}", instanceId);
        }
    }

    private boolean sendOrEnqueue(SignalingEnvelope msg, String reportType, String detail) {
        StreamObserver<SignalingEnvelope> sender = this.signalingSender;
        if (sender != null) {
            try {
                synchronized (senderLock) {
                    sender.onNext(msg);
                }
                log.info("SignalingService: 已上报 {}: {}", reportType, detail);
                return true;
            } catch (Exception e) {
                log.warn("SignalingService: 上报 {} 失败: {}", reportType, detail, e);
                pendingReports.offer(msg);
                return false;
            }
        }
        pendingReports.offer(msg);
        return false;
    }
}
