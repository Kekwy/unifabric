package com.kekwy.unifabric.adapter.provider;

import com.kekwy.unifabric.proto.provider.InstanceStatus;

/**
 * 底层运行时检测到容器级状态变化时的回调（论文 3.2.4）。
 */
@FunctionalInterface
public interface InstanceStatusListener {

    void onStatusChanged(String instanceId, InstanceStatus previous, InstanceStatus current, String message);
}
