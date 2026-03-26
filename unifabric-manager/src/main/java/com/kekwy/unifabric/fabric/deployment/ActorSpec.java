package com.kekwy.unifabric.fabric.deployment;

import com.kekwy.unifabric.proto.common.FunctionDescriptor;
import com.kekwy.unifabric.proto.common.ResourceSpec;
import com.kekwy.unifabric.proto.workflow.NodeKind;

public record ActorSpec(
        String actorId,
        FunctionDescriptor function,
        ResourceSpec resourceSpec,
        String artifactUrl,
        int instanceIndex,
        NodeKind nodeKind
) {
}
