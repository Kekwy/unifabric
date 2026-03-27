package com.kekwy.unifabric.fabric.connectivity;

import com.kekwy.unifabric.proto.provider.InstanceEndpoint;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 已建立的跨域 P2P 隧道出口端点（论文 3.5.3）；未命中时由 {@link ConnectivityResolver} 降级到中继。
 */
@Component
public class PeerTunnelRegistry {

    private final ConcurrentHashMap<String, InstanceEndpoint> instanceIdToTunnelExit = new ConcurrentHashMap<>();

    public void registerTunnelExit(String instanceId, InstanceEndpoint exitEndpoint) {
        if (instanceId != null && !instanceId.isBlank() && exitEndpoint != null) {
            instanceIdToTunnelExit.put(instanceId, exitEndpoint);
        }
    }

    public void remove(String instanceId) {
        if (instanceId != null) {
            instanceIdToTunnelExit.remove(instanceId);
        }
    }

    public Optional<InstanceEndpoint> getTunnelExit(String instanceId) {
        return Optional.ofNullable(instanceIdToTunnelExit.get(instanceId));
    }
}
