package com.kekwy.unifabric.fabric.deployment;

import com.kekwy.unifabric.proto.common.FunctionDescriptor;
import com.kekwy.unifabric.proto.provider.RoutingStrategy;

public record ActorEdge(
        String fromActorId,
        String toActorId,
        FunctionDescriptor functionDescriptor,
        int outputPort,
        RoutingStrategy routingStrategy,
        int inputPort
) {
}
