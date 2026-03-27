package com.kekwy.unifabric.fabric.connectivity;

import com.kekwy.unifabric.proto.provider.InstanceEndpoint;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 全局实例端点表（论文 3.5.2）：由适配器 {@code InstanceEndpointReport} 填充。
 */
@Component
public class InstanceEndpointRegistry {

    public record Entry(String instanceId, String providerId, String host, int port) {}

    private final ConcurrentHashMap<String, Entry> byInstanceId = new ConcurrentHashMap<>();

    public void register(String instanceId, String providerId, InstanceEndpoint endpoint) {
        if (instanceId == null || instanceId.isBlank() || endpoint == null) {
            return;
        }
        String host = endpoint.getHost();
        int port = endpoint.getPort();
        if (host == null || host.isBlank() || port <= 0) {
            return;
        }
        byInstanceId.put(instanceId, new Entry(instanceId, providerId, host, port));
    }

    public Optional<Entry> lookup(String instanceId) {
        return Optional.ofNullable(byInstanceId.get(instanceId));
    }

    public void remove(String instanceId) {
        if (instanceId != null) {
            byInstanceId.remove(instanceId);
        }
    }
}
