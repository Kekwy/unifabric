package com.kekwy.unifabric.common.util;

import com.kekwy.unifabric.proto.common.ResourceSpec;

/**
 * 资源维度解析与向量运算（调度 Filter/Score、乐观记账等复用）。
 */
public final class ResourceSpecUtil {

    private ResourceSpecUtil() {
    }

    /**
     * 将 memory 字段解析为字节数；支持纯数字或带单位字符串的宽松解析（与 NodeDiscoveryManager 行为一致）。
     */
    public static long parseMemoryBytes(String memory) {
        if (memory == null || memory.isBlank()) {
            return 0;
        }
        try {
            return Long.parseLong(memory.replaceAll("[^\\d]", ""));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    public static ResourceSpec zero() {
        return ResourceSpec.newBuilder().setCpu(0).setGpu(0).setMemory("0").build();
    }

    public static ResourceSpec sum(Iterable<ResourceSpec> specs) {
        double cpu = 0;
        double gpu = 0;
        long mem = 0;
        if (specs != null) {
            for (ResourceSpec s : specs) {
                if (s == null) {
                    continue;
                }
                cpu += s.getCpu();
                gpu += s.getGpu();
                mem += parseMemoryBytes(s.getMemory());
            }
        }
        return ResourceSpec.newBuilder()
                .setCpu(cpu)
                .setGpu(gpu)
                .setMemory(Long.toString(mem))
                .build();
    }

    /**
     * 分量非负减法：result = max(0, base - sub).
     */
    public static ResourceSpec subtractNonNegative(ResourceSpec base, ResourceSpec sub) {
        if (base == null) {
            base = zero();
        }
        if (sub == null) {
            sub = zero();
        }
        long baseMem = parseMemoryBytes(base.getMemory());
        long subMem = parseMemoryBytes(sub.getMemory());
        return ResourceSpec.newBuilder()
                .setCpu(Math.max(0, base.getCpu() - sub.getCpu()))
                .setGpu(Math.max(0, base.getGpu() - sub.getGpu()))
                .setMemory(Long.toString(Math.max(0, baseMem - subMem)))
                .build();
    }

    public static boolean isEffectivelyZero(ResourceSpec s) {
        if (s == null) {
            return true;
        }
        return s.getCpu() <= 0 && s.getGpu() <= 0 && parseMemoryBytes(s.getMemory()) <= 0;
    }
}
