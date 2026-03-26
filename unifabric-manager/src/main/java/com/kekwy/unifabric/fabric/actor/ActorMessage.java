package com.kekwy.unifabric.fabric.actor;

import com.kekwy.unifabric.proto.actor.ActorEnvelope;

public record ActorMessage(
        String deploymentId,
        String actorId,
        ActorEnvelope message
) {
}
