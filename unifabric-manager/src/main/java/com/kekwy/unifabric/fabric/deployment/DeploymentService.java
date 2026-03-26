package com.kekwy.unifabric.fabric.deployment;

import com.kekwy.unifabric.fabric.messaging.ActorMessageInbox;

public interface DeploymentService {

    void deploy(DeploymentPlanGraph deploymentPlanGraph,
                ActorMessageInbox inbox,
                DeploymentCallback callback);
}
