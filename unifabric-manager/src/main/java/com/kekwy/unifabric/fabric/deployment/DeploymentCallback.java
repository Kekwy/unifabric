package com.kekwy.unifabric.fabric.deployment;

public interface DeploymentCallback {

    void onSuccess(InstanceRefGraph instanceRefGraph);

    void onFailure(Exception e); // TODO

}
