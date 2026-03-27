package com.kekwy.unifabric.fabric.provider;

import com.kekwy.unifabric.proto.provider.InstanceStatus;

/**
 * 实例容器级状态变更回调（论文 3.2.4），供上层框架订阅。
 */
@FunctionalInterface
public interface InstanceLifecycleListener {

    void onInstanceStatusChanged(String instanceId, String providerId,
                                 InstanceStatus previous, InstanceStatus current, String message);
}
