package com.kekwy.unifabric.fabric.scheduling;

import com.kekwy.unifabric.common.util.ResourceSpecUtil;
import com.kekwy.unifabric.fabric.provider.ProviderInfo;
import com.kekwy.unifabric.proto.common.ResourceCapacity;
import com.kekwy.unifabric.proto.common.ResourceSpec;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Filter 阶段：硬约束筛选（论文 3.4.3）。
 */
public final class ProviderFilter {

    private ProviderFilter() {
    }

    public static List<ProviderInfo> filterLocalCandidates(DeployRequest request, List<ProviderInfo> providers) {
        ResourceSpec demand = request.getDemand();
        String typeConstraint = request.getProviderTypeConstraint();
        List<String> requiredTags = request.getRequiredTags();

        return providers.stream()
                .filter(p -> p.getStatus() == ProviderInfo.Status.ONLINE)
                .filter(p -> typeCompatible(p, typeConstraint))
                .filter(p -> tagsSatisfied(p, requiredTags))
                .filter(p -> resourcesSatisfied(p, demand))
                .collect(Collectors.toList());
    }

    private static boolean typeCompatible(ProviderInfo p, String typeConstraint) {
        if (typeConstraint == null || typeConstraint.isBlank()) {
            return true;
        }
        return typeConstraint.equalsIgnoreCase(p.getProviderType());
    }

    private static boolean tagsSatisfied(ProviderInfo p, List<String> requiredTags) {
        if (requiredTags == null || requiredTags.isEmpty()) {
            return true;
        }
        Set<String> nodeTags = new LinkedHashSet<>(p.getTags());
        for (String t : requiredTags) {
            if (t == null || t.isBlank()) {
                continue;
            }
            if (!nodeTags.contains(t)) {
                return false;
            }
        }
        return true;
    }

    private static boolean resourcesSatisfied(ProviderInfo p, ResourceSpec demand) {
        if (demand == null || ResourceSpecUtil.isEffectivelyZero(demand)) {
            return true;
        }
        ResourceCapacity effective = p.getEffectiveResourceCapacity();
        if (effective == null || !effective.hasAvailable()) {
            return false;
        }
        ResourceSpec avail = effective.getAvailable();
        if (demand.getCpu() > 0 && avail.getCpu() + 1e-9 < demand.getCpu()) {
            return false;
        }
        if (demand.getGpu() > 0 && avail.getGpu() + 1e-9 < demand.getGpu()) {
            return false;
        }
        long needMem = ResourceSpecUtil.parseMemoryBytes(demand.getMemory());
        if (needMem > 0) {
            long haveMem = ResourceSpecUtil.parseMemoryBytes(avail.getMemory());
            if (haveMem < needMem) {
                return false;
            }
        }
        return true;
    }
}
