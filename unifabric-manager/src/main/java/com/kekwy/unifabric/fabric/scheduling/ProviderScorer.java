package com.kekwy.unifabric.fabric.scheduling;

import com.kekwy.unifabric.common.util.ResourceSpecUtil;
import com.kekwy.unifabric.fabric.provider.ProviderInfo;
import com.kekwy.unifabric.proto.common.ResourceCapacity;
import com.kekwy.unifabric.proto.common.ResourceSpec;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Score 阶段：LeastRequested + BalancedAllocation（论文 3.4.3 式 (7)(9)(10)）。
 */
public final class ProviderScorer {

    private ProviderScorer() {
    }

    public static double score(ProviderInfo provider, ResourceSpec demand, double alpha) {
        ResourceCapacity cap = provider.getResourceCapacity();
        ResourceSpec total = cap != null && cap.hasTotal() ? cap.getTotal() : ResourceSpecUtil.zero();
        ResourceSpec availEff = provider.getEffectiveResourceCapacity() != null
                && provider.getEffectiveResourceCapacity().hasAvailable()
                ? provider.getEffectiveResourceCapacity().getAvailable()
                : ResourceSpecUtil.zero();

        List<Dimension> dims = activeDimensions(demand);
        if (dims.isEmpty()) {
            return 0.5;
        }

        double fLr = scoreLeastRequested(total, availEff, demand, dims);
        double fBa = scoreBalancedAllocation(total, availEff, demand, dims);
        double a = clamp(alpha, 0, 1);
        return a * fLr + (1 - a) * fBa;
    }

    /**
     * 在得分最高的 Provider 中随机择一（论文 3.4.3）。
     */
    public static ProviderInfo pickRandomTie(List<ScoredProvider> scored) {
        if (scored.isEmpty()) {
            return null;
        }
        double best = scored.get(0).score();
        List<ProviderInfo> tops = new ArrayList<>();
        for (ScoredProvider sp : scored) {
            if (Math.abs(sp.score() - best) < 1e-12) {
                tops.add(sp.provider());
            }
        }
        return tops.get(ThreadLocalRandom.current().nextInt(tops.size()));
    }

    public record ScoredProvider(ProviderInfo provider, double score) {
    }

    private enum Dimension {
        CPU, MEM, GPU
    }

    private static List<Dimension> activeDimensions(ResourceSpec demand) {
        List<Dimension> out = new ArrayList<>();
        if (demand.getCpu() > 0) {
            out.add(Dimension.CPU);
        }
        if (ResourceSpecUtil.parseMemoryBytes(demand.getMemory()) > 0) {
            out.add(Dimension.MEM);
        }
        if (demand.getGpu() > 0) {
            out.add(Dimension.GPU);
        }
        return out;
    }

    private static double scoreLeastRequested(ResourceSpec total,
                                              ResourceSpec avail,
                                              ResourceSpec demand,
                                              List<Dimension> dims) {
        double sum = 0;
        for (Dimension d : dims) {
            double c = capacity(total, d);
            double a = available(avail, d);
            double r = requested(demand, d);
            if (c <= 1e-12) {
                sum += 0;
            } else {
                sum += clamp01((a - r) / c);
            }
        }
        return sum / dims.size();
    }

    private static double scoreBalancedAllocation(ResourceSpec total,
                                                  ResourceSpec avail,
                                                  ResourceSpec demand,
                                                  List<Dimension> dims) {
        double sumRho = 0;
        List<Double> rhos = new ArrayList<>();
        for (Dimension d : dims) {
            double c = capacity(total, d);
            double a = available(avail, d);
            double r = requested(demand, d);
            double uEff = Math.max(0, c - a);
            double rho = c > 1e-12 ? (uEff + r) / c : 0;
            rho = clamp01(rho);
            rhos.add(rho);
            sumRho += rho;
        }
        double mean = sumRho / dims.size();
        double varSum = 0;
        for (double rho : rhos) {
            double diff = rho - mean;
            varSum += diff * diff;
        }
        double variance = varSum / dims.size();
        double std = Math.sqrt(variance);
        return clamp01(1 - std);
    }

    private static double capacity(ResourceSpec total, Dimension d) {
        return switch (d) {
            case CPU -> Math.max(0, total.getCpu());
            case GPU -> Math.max(0, total.getGpu());
            case MEM -> Math.max(0, (double) ResourceSpecUtil.parseMemoryBytes(total.getMemory()));
        };
    }

    private static double available(ResourceSpec avail, Dimension d) {
        return switch (d) {
            case CPU -> Math.max(0, avail.getCpu());
            case GPU -> Math.max(0, avail.getGpu());
            case MEM -> Math.max(0, (double) ResourceSpecUtil.parseMemoryBytes(avail.getMemory()));
        };
    }

    private static double requested(ResourceSpec demand, Dimension d) {
        return switch (d) {
            case CPU -> Math.max(0, demand.getCpu());
            case GPU -> Math.max(0, demand.getGpu());
            case MEM -> Math.max(0, (double) ResourceSpecUtil.parseMemoryBytes(demand.getMemory()));
        };
    }

    private static double clamp01(double x) {
        if (x < 0) {
            return 0;
        }
        if (x > 1) {
            return 1;
        }
        return x;
    }

    private static double clamp(double x, double lo, double hi) {
        return Math.max(lo, Math.min(hi, x));
    }
}
