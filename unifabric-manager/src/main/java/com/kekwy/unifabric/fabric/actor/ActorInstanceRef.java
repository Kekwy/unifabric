package com.kekwy.unifabric.fabric.actor;

import com.kekwy.unifabric.proto.actor.ActorEnvelope;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.RequiredArgsConstructor;
import lombok.ToString;

import java.util.List;

@Data
@RequiredArgsConstructor
public class ActorInstanceRef {

    private final String actorId;

    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private ActorRegistry actorRegistry;

    private List<ActorInstanceRef> precursors;
    private List<ActorInstanceRef> successors;

    public void send(ActorEnvelope message) {
        if (actorRegistry == null) {
            throw new IllegalStateException("ActorRegistry 未设置: actorId=" + actorId);
        }
        actorRegistry.sendToActor(actorId, message);
    }

}
