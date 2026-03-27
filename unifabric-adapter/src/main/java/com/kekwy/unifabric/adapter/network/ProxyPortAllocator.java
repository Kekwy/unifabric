package com.kekwy.unifabric.adapter.network;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * 代理监听端口区间轮询分配（论文 3.5.2–3.5.3）。
 * {@code min=0} 或 {@code max < min} 表示禁用，由调用方改用系统临时端口。
 */
public final class ProxyPortAllocator {

    private final int minPort;
    private final int maxPort;
    private final AtomicInteger cursor;

    public ProxyPortAllocator(int minPort, int maxPort) {
        this.minPort = minPort;
        this.maxPort = maxPort;
        this.cursor = new AtomicInteger(minPort > 0 ? minPort : 0);
    }

    public boolean isEnabled() {
        return minPort > 0 && maxPort >= minPort;
    }

    /** 区间内端口个数；未启用时为 0。 */
    public int rangeSize() {
        return isEnabled() ? maxPort - minPort + 1 : 0;
    }

    /**
     * 返回区间内下一个候选端口（轮询）；未启用时返回 0。
     */
    public int nextCandidatePort() {
        if (!isEnabled()) {
            return 0;
        }
        int span = maxPort - minPort + 1;
        int offset = Math.floorMod(cursor.getAndIncrement(), span);
        return minPort + offset;
    }
}
