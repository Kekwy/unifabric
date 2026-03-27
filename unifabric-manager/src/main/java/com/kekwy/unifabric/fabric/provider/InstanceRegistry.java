package com.kekwy.unifabric.fabric.provider;

import com.kekwy.unifabric.proto.provider.InstanceStatus;

import java.util.Optional;

/**
 * 管理节点实例状态表（论文 3.2.4）。
 */
public interface InstanceRegistry {

    void trackInstance(String instanceId, String providerId);

    void updateStatus(String instanceId, String providerId,
                      InstanceStatus previous, InstanceStatus current, String message);

    /** 论文 3.5.2：更新实例在 Provider 本地的网络端点 */
    void updateLocalEndpoint(String instanceId, String providerId, String host, int port);

    Optional<InstanceRecord> getRecord(String instanceId);

    void addListener(InstanceLifecycleListener listener);
}
