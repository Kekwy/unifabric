package com.kekwy.unifabric.fabric.scheduling;

import com.kekwy.unifabric.fabric.discovery.NodeDiscoveryManager;
import com.kekwy.unifabric.fabric.discovery.PeerNode;
import com.kekwy.unifabric.fabric.provider.ProviderInfo;
import com.kekwy.unifabric.fabric.provider.ProviderRegistry;
import com.kekwy.unifabric.proto.common.ResourceSpec;
import com.kekwy.unifabric.proto.fabric.ScheduleConstraints;
import com.kekwy.unifabric.proto.fabric.ScheduleDeployRequest;
import com.kekwy.unifabric.proto.fabric.ScheduleDeployResponse;
import com.kekwy.unifabric.proto.provider.DeploymentEnvelope;
import com.kekwy.unifabric.proto.provider.DeployInstanceRequest;
import com.kekwy.unifabric.proto.provider.InstanceInfo;
import com.kekwy.unifabric.proto.provider.InstanceStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * 域内优先 + 跨域升级 + 乐观记账（论文 3.4）。
 */
@Service
public class DefaultSchedulingService implements SchedulingService {

    private static final Logger log = LoggerFactory.getLogger(DefaultSchedulingService.class);

    private final ProviderRegistry providerRegistry;
    private final NodeDiscoveryManager nodeDiscoveryManager;
    private final CrossDomainSchedulingClient crossDomainClient;
    private final SchedulingProperties properties;

    private final ExecutorService async = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "scheduling-async");
        t.setDaemon(true);
        return t;
    });

    public DefaultSchedulingService(ProviderRegistry providerRegistry,
                                    NodeDiscoveryManager nodeDiscoveryManager,
                                    CrossDomainSchedulingClient crossDomainClient,
                                    SchedulingProperties properties) {
        this.providerRegistry = providerRegistry;
        this.nodeDiscoveryManager = nodeDiscoveryManager;
        this.crossDomainClient = crossDomainClient;
        this.properties = properties;
    }

    @Override
    public CompletableFuture<SchedulingResult> schedule(DeployRequest request) {
        return CompletableFuture.supplyAsync(() -> scheduleBlocking(request), async);
    }

    @Override
    public SchedulingResult scheduleBlocking(DeployRequest request) {
        SchedulingResult local = tryLocalSchedule(request);
        if (local != null) {
            return local;
        }
        return tryCrossDomainSchedule(request);
    }

    /**
     * @return 非 null 表示本地流程已结束（成功）；null 表示需跨域
     */
    private SchedulingResult tryLocalSchedule(DeployRequest request) {
        Set<String> excluded = new HashSet<>();
        while (true) {
            List<ProviderInfo> candidates = ProviderFilter.filterLocalCandidates(
                            request, providerRegistry.listOnlineProviders()).stream()
                    .filter(p -> !excluded.contains(p.getProviderId()))
                    .toList();
            if (candidates.isEmpty()) {
                return null;
            }
            List<ProviderScorer.ScoredProvider> scored = new ArrayList<>();
            for (ProviderInfo p : candidates) {
                double sc = ProviderScorer.score(p, request.getDemand(), properties.getAlpha());
                scored.add(new ProviderScorer.ScoredProvider(p, sc));
            }
            scored.sort((a, b) -> Double.compare(b.score(), a.score()));
            ProviderInfo best = ProviderScorer.pickRandomTie(scored);
            if (best == null) {
                return null;
            }
            String pendingKey = request.getRequestId() + ":" + best.getProviderId();
            ResourceSpec demand = request.getDemand();
            if (!best.addPendingDeduction(pendingKey, demand)) {
                excluded.add(best.getProviderId());
                continue;
            }
            try {
                SchedulingResult result = deployToProvider(request, best.getProviderId());
                best.removePendingDeduction(pendingKey);
                if (result.isSuccess()) {
                    return result;
                }
                log.debug("域内部署失败 providerId={} msg={}", best.getProviderId(), result.getErrorMessage());
                excluded.add(best.getProviderId());
            } catch (Exception e) {
                best.removePendingDeduction(pendingKey);
                log.warn("域内部署异常 providerId={}", best.getProviderId(), e);
                excluded.add(best.getProviderId());
            }
        }
    }

    private SchedulingResult deployToProvider(DeployRequest request, String providerId) throws Exception {
        DeployInstanceRequest deployReq = DeployInstanceRequest.newBuilder()
                .setInstanceId(request.getInstanceId())
                .setImage(request.getImage() != null ? request.getImage() : "")
                .setResourceRequest(request.getDemand())
                .putAllEnv(request.getEnv())
                .putAllLabels(request.getLabels())
                .setArtifactUrl(request.getArtifactUrl() != null ? request.getArtifactUrl() : "")
                .build();
        DeploymentEnvelope.Builder env = DeploymentEnvelope.newBuilder()
                .setDeployInstanceRequest(deployReq);
        CompletableFuture<DeploymentEnvelope> fut = providerRegistry.sendDeployment(providerId, env);
        DeploymentEnvelope resp = fut.get(properties.getDeployTimeoutSeconds(), TimeUnit.SECONDS);
        return interpretDeployResponse(providerId, resp);
    }

    private static SchedulingResult interpretDeployResponse(String providerId, DeploymentEnvelope resp) {
        if (resp == null) {
            return SchedulingResult.failure(providerId, "empty deployment response");
        }
        if (!resp.getError().isEmpty()) {
            return SchedulingResult.failure(providerId, resp.getError());
        }
        if (resp.getMessageCase() != DeploymentEnvelope.MessageCase.DEPLOY_INSTANCE_RESPONSE) {
            return SchedulingResult.failure(providerId, "unexpected envelope: " + resp.getMessageCase());
        }
        InstanceInfo info = resp.getDeployInstanceResponse().getInstance();
        InstanceStatus st = info.getStatus();
        if (st == InstanceStatus.FAILED) {
            return SchedulingResult.failure(providerId,
                    info.getMessage().isEmpty() ? "instance FAILED" : info.getMessage());
        }
        return SchedulingResult.ok(providerId, info);
    }

    private SchedulingResult tryCrossDomainSchedule(DeployRequest request) {
        List<PeerNode> peers = nodeDiscoveryManager.filterCandidateNodes(
                request.getDemand(), request.getRequiredTags());
        Set<String> triedDomains = new HashSet<>();
        String localDomain = nodeDiscoveryManager.getDomainId();
        String localNodeId = nodeDiscoveryManager.getLocalNodeId();
        for (PeerNode n : peers) {
            if (n.getNodeId().equals(localNodeId)) {
                continue;
            }
            String dom = n.getDomainId();
            if (dom == null || dom.isBlank() || dom.equals(localDomain)) {
                continue;
            }
            if (dom.equals(request.getOriginatingDomainId())) {
                continue;
            }
            if (!triedDomains.add(dom)) {
                continue;
            }
            ScheduleDeployRequest proto = toScheduleProto(request, localDomain);
            ScheduleDeployResponse resp = crossDomainClient.scheduleDeploy(n.getAddress(), proto);
            if (resp.getSuccess()) {
                InstanceInfo inst = resp.hasInstance() ? resp.getInstance() : null;
                return SchedulingResult.ok(resp.getTargetProviderId(), inst);
            }
            log.debug("远程域调度失败 domainId={} addr={} msg={}", dom, n.getAddress(), resp.getErrorMessage());
        }
        return SchedulingResult.failure("域内无可用 Provider 且跨域调度全部失败");
    }

    static ScheduleDeployRequest toScheduleProto(DeployRequest request, String originatingDomainId) {
        ScheduleConstraints.Builder c = ScheduleConstraints.newBuilder();
        String pt = request.getProviderTypeConstraint();
        if (pt != null && !pt.isBlank()) {
            c.setProviderType(pt);
        }
        c.addAllRequiredTags(request.getRequiredTags());
        return ScheduleDeployRequest.newBuilder()
                .setRequestId(request.getRequestId())
                .setDemand(request.getDemand())
                .setImage(request.getImage() != null ? request.getImage() : "")
                .setConstraints(c)
                .putAllEnv(request.getEnv())
                .putAllLabels(request.getLabels())
                .setArtifactUrl(request.getArtifactUrl() != null ? request.getArtifactUrl() : "")
                .setOriginatingDomainId(originatingDomainId != null ? originatingDomainId : "")
                .build();
    }
}
