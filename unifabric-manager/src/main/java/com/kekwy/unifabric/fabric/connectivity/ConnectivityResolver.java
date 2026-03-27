package com.kekwy.unifabric.fabric.connectivity;

import com.kekwy.unifabric.fabric.provider.ProviderInfo;
import com.kekwy.unifabric.fabric.provider.ProviderRegistry;
import com.kekwy.unifabric.proto.provider.InstanceEndpoint;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * 根据源 Provider、目标实例拓扑关系选择连通层级（论文 3.5.3）。
 */
@Component
public class ConnectivityResolver {

    public ConnectivityDecision resolve(String sourceProviderId,
                                        String targetInstanceId,
                                        InstanceEndpointRegistry endpointRegistry,
                                        ProviderRegistry providerRegistry,
                                        PeerTunnelRegistry tunnelRegistry) {
        var entry = endpointRegistry.lookup(targetInstanceId);
        if (entry.isEmpty()) {
            return new ConnectivityDecision(null, null, null, "目标实例端点未注册");
        }
        InstanceEndpointRegistry.Entry e = entry.get();
        InstanceEndpoint local = InstanceEndpoint.newBuilder()
                .setHost(e.host())
                .setPort(e.port())
                .build();

        if (Objects.equals(e.providerId(), sourceProviderId)) {
            return new ConnectivityDecision(
                    ConnectivityDecision.TIER_LOCAL, e.providerId(), local, null);
        }

        ProviderInfo src = providerRegistry.getProvider(sourceProviderId);
        ProviderInfo tgt = providerRegistry.getProvider(e.providerId());
        if (src == null || tgt == null) {
            return new ConnectivityDecision(null, null, null, "Provider 不存在");
        }
        if (zoneEquals(src.getZone(), tgt.getZone())) {
            return new ConnectivityDecision(
                    ConnectivityDecision.TIER_INTRA_DOMAIN, e.providerId(), local, null);
        }

        var tunnel = tunnelRegistry.getTunnelExit(targetInstanceId);
        if (tunnel.isPresent()) {
            return new ConnectivityDecision(
                    ConnectivityDecision.TIER_CROSS_DOMAIN_TUNNEL, e.providerId(), tunnel.get(), null);
        }
        return new ConnectivityDecision(
                ConnectivityDecision.TIER_RELAY, e.providerId(), local, null);
    }

    private static boolean zoneEquals(String a, String b) {
        return Objects.equals(a != null ? a : "", b != null ? b : "");
    }
}
