package com.kekwy.unifabric.fabric.messaging;

import com.kekwy.unifabric.proto.actor.ActorEnvelope;

@FunctionalInterface
public interface ActorMessageHandler {

    void handleActorMessage(String nodeId, String actorId, ActorEnvelope message);

}
