package com.kekwy.unifabric.fabric.connectivity;

import com.kekwy.unifabric.fabric.provider.InstanceRegistry;
import com.kekwy.unifabric.fabric.provider.ProviderRegistry;
import com.kekwy.unifabric.proto.provider.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 信令编排：端点注册、端点解析、{@code ConnectInstruction} 下发与候选转发（论文 3.5.4）。
 */
@Service
public class ConnectivityCoordinator {

    private static final Logger log = LoggerFactory.getLogger(ConnectivityCoordinator.class);

    private final ProviderRegistry providerRegistry;
    private final InstanceEndpointRegistry endpointRegistry;
    private final InstanceRegistry instanceRegistry;
    private final ConnectivityResolver resolver;
    private final RelayService relayService;
    private final PeerTunnelRegistry tunnelRegistry;

    private record ConnectSession(String initiatorProviderId, String peerProviderId) {}

    private record PendingResolve(String messageId, String requesterProviderId) {}

    private final ConcurrentHashMap<String, ConnectSession> sessionsByConnectId = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, PendingResolve> pendingResolveByConnectId = new ConcurrentHashMap<>();

    public ConnectivityCoordinator(ProviderRegistry providerRegistry,
                                   InstanceEndpointRegistry endpointRegistry,
                                   InstanceRegistry instanceRegistry,
                                   ConnectivityResolver resolver,
                                   RelayService relayService,
                                   PeerTunnelRegistry tunnelRegistry) {
        this.providerRegistry = providerRegistry;
        this.endpointRegistry = endpointRegistry;
        this.instanceRegistry = instanceRegistry;
        this.resolver = resolver;
        this.relayService = relayService;
        this.tunnelRegistry = tunnelRegistry;
    }

    public void onInstanceEndpointReport(String fromProviderId, InstanceEndpointReport report) {
        if (report == null) {
            return;
        }
        endpointRegistry.register(report.getInstanceId(), fromProviderId, report.getEndpoint());
        InstanceEndpoint ep = report.getEndpoint();
        if (ep != null && ep.getPort() > 0 && ep.getHost() != null && !ep.getHost().isBlank()) {
            instanceRegistry.updateLocalEndpoint(report.getInstanceId(), fromProviderId, ep.getHost(), ep.getPort());
        }
        log.debug("已登记实例端点: instanceId={}, providerId={}", report.getInstanceId(), fromProviderId);
    }

    public void onResolveEndpointRequest(String fromProviderId, ResolveEndpointRequest req) {
        if (req == null) {
            return;
        }
        String msgId = req.getMessageId();
        String srcProv = req.getSourceProviderId();
        String tgtInst = req.getTargetInstanceId();
        if (msgId == null || msgId.isBlank() || srcProv == null || srcProv.isBlank()
                || tgtInst == null || tgtInst.isBlank()) {
            sendResolveError(fromProviderId, msgId != null ? msgId : "", "ResolveEndpointRequest 字段不完整");
            return;
        }
        if (!srcProv.equals(fromProviderId)) {
            sendResolveError(fromProviderId, msgId, "source_provider_id 与信令连接不一致");
            return;
        }

        ConnectivityDecision d = resolver.resolve(
                srcProv, tgtInst, endpointRegistry, providerRegistry, tunnelRegistry);
        if (!d.success()) {
            sendResolveError(fromProviderId, msgId, d.errorMessage() != null ? d.errorMessage() : "解析失败");
            return;
        }

        String connectId = UUID.randomUUID().toString();
        InstanceEndpoint targetLocal = d.targetLocalEndpoint();
        String tier = d.connectivityTier();
        String targetProv = d.targetProviderId();

        if (ConnectivityDecision.TIER_LOCAL.equals(tier)) {
            sendLocalResolve(fromProviderId, msgId, connectId, targetLocal);
            return;
        }
        if (ConnectivityDecision.TIER_CROSS_DOMAIN_TUNNEL.equals(tier)) {
            sendResolveWithTier(fromProviderId, msgId, connectId, targetLocal,
                    ConnectivityDecision.TIER_CROSS_DOMAIN_TUNNEL);
            return;
        }
        if (ConnectivityDecision.TIER_INTRA_DOMAIN.equals(tier)) {
            startIntraDomain(connectId, msgId, fromProviderId, srcProv, targetProv, targetLocal);
            return;
        }
        if (ConnectivityDecision.TIER_RELAY.equals(tier)) {
            startRelay(connectId, msgId, fromProviderId, srcProv, targetProv, targetLocal);
            return;
        }
        sendResolveError(fromProviderId, msgId, "未知连通层级: " + tier);
    }

    public void onCandidateUpdate(String fromProviderId, CandidateUpdate update) {
        if (update == null) {
            return;
        }
        String cid = update.getConnectId();
        if (cid == null || cid.isBlank()) {
            return;
        }
        ConnectSession sess = sessionsByConnectId.get(cid);
        if (sess != null) {
            String peer = fromProviderId.equals(sess.initiatorProviderId())
                    ? sess.peerProviderId()
                    : sess.initiatorProviderId();
            sendSignaling(peer, SignalingEnvelope.newBuilder()
                    .setTimestampMs(System.currentTimeMillis())
                    .setCandidateUpdate(update)
                    .build());
        }

        PendingResolve pend = pendingResolveByConnectId.get(cid);
        if (pend != null && sess != null && fromProviderId.equals(sess.initiatorProviderId())) {
            for (NetworkCandidate c : update.getCandidatesList()) {
                if (c.getType() == NetworkCandidate.CandidateType.DIRECT && c.getPort() > 0
                        && c.getHost() != null && !c.getHost().isBlank()) {
                    InstanceEndpoint proxy = InstanceEndpoint.newBuilder()
                            .setHost(c.getHost())
                            .setPort(c.getPort())
                            .build();
                    sendSignaling(pend.requesterProviderId(), SignalingEnvelope.newBuilder()
                            .setTimestampMs(System.currentTimeMillis())
                            .setResolveEndpointResponse(ResolveEndpointResponse.newBuilder()
                                    .setCorrelationId(pend.messageId())
                                    .setConnectId(cid)
                                    .setProxyEndpoint(proxy)
                                    .setConnectivityTier(ConnectivityDecision.TIER_INTRA_DOMAIN)
                                    .build())
                            .build());
                    pendingResolveByConnectId.remove(cid);
                    log.info("已下发 INTRA_DOMAIN 解析结果: connectId={}, proxy={}:{}",
                            cid, c.getHost(), c.getPort());
                    break;
                }
            }
        }
    }

    public void onIceEnvelope(String fromProviderId, IceEnvelope ice) {
        if (ice == null) {
            return;
        }
        String to = ice.getToProviderId();
        if (to == null || to.isBlank()) {
            return;
        }
        sendSignaling(to, SignalingEnvelope.newBuilder()
                .setTimestampMs(System.currentTimeMillis())
                .setIceEnvelope(ice)
                .build());
    }

    private void startIntraDomain(String connectId,
                                  String msgId,
                                  String requesterProviderId,
                                  String initiatorProviderId,
                                  String responderProviderId,
                                  InstanceEndpoint targetLocal) {
        sessionsByConnectId.put(connectId, new ConnectSession(initiatorProviderId, responderProviderId));
        pendingResolveByConnectId.put(connectId, new PendingResolve(msgId, requesterProviderId));

        String dst = targetLocal.getHost() + ":" + targetLocal.getPort();
        sendSignaling(responderProviderId, signalingConnect(false, connectId, dst, initiatorProviderId, "", 0));
        pauseBetweenInstructions();
        sendSignaling(initiatorProviderId, signalingConnect(true, connectId, "", responderProviderId, "", 0));
        log.info("已下发同域跨 Provider 连通指令: connectId={}, {} -> {}",
                connectId, initiatorProviderId, responderProviderId);
    }

    private void startRelay(String connectId,
                            String msgId,
                            String requesterProviderId,
                            String initiatorProviderId,
                            String responderProviderId,
                            InstanceEndpoint targetLocal) {
        try {
            RelayService.RelayEndpoint re = relayService.allocateRelay(connectId);
            InstanceEndpoint relayEp = InstanceEndpoint.newBuilder()
                    .setHost(re.host())
                    .setPort(re.port())
                    .build();
            sendSignaling(requesterProviderId, SignalingEnvelope.newBuilder()
                    .setTimestampMs(System.currentTimeMillis())
                    .setResolveEndpointResponse(ResolveEndpointResponse.newBuilder()
                            .setCorrelationId(msgId)
                            .setConnectId(connectId)
                            .setProxyEndpoint(relayEp)
                            .setConnectivityTier(ConnectivityDecision.TIER_RELAY)
                            .build())
                    .build());

            String dst = targetLocal.getHost() + ":" + targetLocal.getPort();
            sessionsByConnectId.put(connectId, new ConnectSession(initiatorProviderId, responderProviderId));
            sendSignaling(responderProviderId,
                    signalingConnect(false, connectId, dst, initiatorProviderId, re.host(), re.port()));
            pauseBetweenInstructions();
            sendSignaling(initiatorProviderId,
                    signalingConnect(true, connectId, "", responderProviderId, re.host(), re.port()));
            log.info("已下发中继连通指令: connectId={}, relay={}:{}", connectId, re.host(), re.port());
        } catch (IOException e) {
            log.error("分配中继端口失败: connectId={}", connectId, e);
            sendResolveError(requesterProviderId, msgId, "中继分配失败: " + e.getMessage());
        }
    }

    private static void pauseBetweenInstructions() {
        try {
            Thread.sleep(50L);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static SignalingEnvelope signalingConnect(boolean initiator,
                                                      String connectId,
                                                      String dstAddr,
                                                      String peerProviderId,
                                                      String relayHost,
                                                      int relayPort) {
        ConnectInstruction.Builder b = ConnectInstruction.newBuilder()
                .setConnectId(connectId)
                .setDstInstanceAddr(dstAddr != null ? dstAddr : "")
                .setInitiator(initiator)
                .setPeerProviderId(peerProviderId != null ? peerProviderId : "");
        if (relayHost != null && !relayHost.isBlank() && relayPort > 0) {
            b.setRelayHost(relayHost).setRelayPort(relayPort);
        }
        return SignalingEnvelope.newBuilder()
                .setTimestampMs(System.currentTimeMillis())
                .setConnectInstruction(b.build())
                .build();
    }

    private void sendLocalResolve(String toProviderId, String msgId, String connectId, InstanceEndpoint ep) {
        sendResolveWithTier(toProviderId, msgId, connectId, ep, ConnectivityDecision.TIER_LOCAL);
    }

    private void sendResolveWithTier(String toProviderId, String msgId, String connectId,
                                    InstanceEndpoint ep, String tier) {
        sendSignaling(toProviderId, SignalingEnvelope.newBuilder()
                .setTimestampMs(System.currentTimeMillis())
                .setResolveEndpointResponse(ResolveEndpointResponse.newBuilder()
                        .setCorrelationId(msgId)
                        .setConnectId(connectId)
                        .setProxyEndpoint(ep)
                        .setConnectivityTier(tier != null ? tier : "")
                        .build())
                .build());
    }

    private void sendResolveError(String toProviderId, String msgId, String err) {
        sendSignaling(toProviderId, SignalingEnvelope.newBuilder()
                .setTimestampMs(System.currentTimeMillis())
                .setResolveEndpointResponse(ResolveEndpointResponse.newBuilder()
                        .setCorrelationId(msgId != null ? msgId : "")
                        .setConnectivityTier("")
                        .setErrorMessage(err != null ? err : "")
                        .build())
                .build());
    }

    private void sendSignaling(String providerId, SignalingEnvelope envelope) {
        var conn = providerRegistry.getConnection(providerId);
        if (conn == null) {
            log.warn("无法转发信令: Provider 无连接对象 providerId={}", providerId);
            return;
        }
        conn.sendSignaling(envelope);
    }
}
