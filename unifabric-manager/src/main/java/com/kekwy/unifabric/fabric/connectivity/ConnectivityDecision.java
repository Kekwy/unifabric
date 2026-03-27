package com.kekwy.unifabric.fabric.connectivity;

import com.kekwy.unifabric.proto.provider.InstanceEndpoint;

/**
 * 分级连通策略判定结果（论文 3.5.3）。
 */
public record ConnectivityDecision(
        String connectivityTier,
        String targetProviderId,
        InstanceEndpoint targetLocalEndpoint,
        String errorMessage
) {
    public static final String TIER_LOCAL = "LOCAL";
    public static final String TIER_INTRA_DOMAIN = "INTRA_DOMAIN";
    public static final String TIER_CROSS_DOMAIN_TUNNEL = "CROSS_DOMAIN_TUNNEL";
    public static final String TIER_RELAY = "RELAY";

    public boolean success() {
        return errorMessage == null || errorMessage.isEmpty();
    }
}
