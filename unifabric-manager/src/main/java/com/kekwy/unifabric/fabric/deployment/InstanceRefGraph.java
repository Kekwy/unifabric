package com.kekwy.unifabric.fabric.deployment;

import com.kekwy.unifabric.fabric.actor.ActorInstanceRef;

import java.util.List;

public record InstanceRefGraph(
        String deploymentId,
        List<ActorInstanceRef> actorInstanceRefs
) {
}
