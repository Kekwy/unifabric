package com.kekwy.unifabric.fabric.deployment;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 维护一次 deployment 的 actor 就绪与链路连通状态。
 * <p>
 * 当所有 actor 上报 ready 且所有预期链路上报 connected 后，
 * 通过 {@link DeploymentCallback#onSuccess(InstanceRefGraph)} 通知上层。
 */
class DeploymentContext {

    private static final Logger log = LoggerFactory.getLogger(DeploymentContext.class);

    private final String deploymentId;
    private final Set<String> allActorIds;
    private final Set<EdgeKey> expectedEdges;
    private final DeploymentCallback callback;
    private final InstanceRefGraph instanceRefGraph;

    private final Set<String> readyActorIds = ConcurrentHashMap.newKeySet();
    private final Set<EdgeKey> connectedEdges = ConcurrentHashMap.newKeySet();
    private volatile boolean completed = false;

    record EdgeKey(String fromActorId, String toActorId) {
    }

    DeploymentContext(String deploymentId,
                      Set<String> allActorIds,
                      Set<EdgeKey> expectedEdges,
                      DeploymentCallback callback,
                      InstanceRefGraph instanceRefGraph) {
        this.deploymentId = Objects.requireNonNull(deploymentId);
        this.allActorIds = Set.copyOf(allActorIds);
        this.expectedEdges = Set.copyOf(expectedEdges);
        this.callback = Objects.requireNonNull(callback);
        this.instanceRefGraph = Objects.requireNonNull(instanceRefGraph);
    }

    String getDeploymentId() {
        return deploymentId;
    }

    boolean containsActor(String actorId) {
        return allActorIds.contains(actorId);
    }

    boolean containsEdge(String fromActorId, String toActorId) {
        return expectedEdges.contains(new EdgeKey(fromActorId, toActorId));
    }

    void onActorReady(String actorId) {
        if (completed || !allActorIds.contains(actorId)) {
            return;
        }
        readyActorIds.add(actorId);
        log.debug("Actor ready: deploymentId={}, actorId={}, progress={}/{}",
                deploymentId, actorId, readyActorIds.size(), allActorIds.size());
        checkComplete();
    }

    void onChannelConnected(String fromActorId, String toActorId) {
        if (completed) {
            return;
        }
        EdgeKey key = new EdgeKey(fromActorId, toActorId);
        if (!expectedEdges.contains(key)) {
            return;
        }
        connectedEdges.add(key);
        log.debug("Channel connected: deploymentId={}, {}→{}, progress={}/{}",
                deploymentId, fromActorId, toActorId, connectedEdges.size(), expectedEdges.size());
        checkComplete();
    }

    private void checkComplete() {
        if (completed) {
            return;
        }
        if (!readyActorIds.containsAll(allActorIds)) {
            return;
        }
        if (!connectedEdges.containsAll(expectedEdges)) {
            return;
        }
        completed = true;
        log.info("Deployment ready: deploymentId={}, actors={}, edges={}",
                deploymentId, allActorIds.size(), expectedEdges.size());
        callback.onSuccess(instanceRefGraph);
    }
}
