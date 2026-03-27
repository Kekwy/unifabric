package com.kekwy.unifabric.fabric.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * Gossip 跨域发现参数（论文 3.3.2 / 表 gossip-params）。
 */
@ConfigurationProperties(prefix = "unifabric.fabric.gossip")
public class FabricGossipProperties {

    /**
     * 种子节点 gRPC 地址（host:port），用于引导。
     */
    private List<String> seedNodes = new ArrayList<>();

    /**
     * 通信周期 Δ（秒）。
     */
    private int intervalSeconds = 5;

    /**
     * 每轮随机交换的对等节点数 k。
     */
    private int fanOut = 1;

    /**
     * 反熵校准周期（分钟）。
     */
    private int antiEntropyIntervalMinutes = 3;

    public List<String> getSeedNodes() {
        return seedNodes;
    }

    public void setSeedNodes(List<String> seedNodes) {
        this.seedNodes = seedNodes != null ? new ArrayList<>(seedNodes) : new ArrayList<>();
    }

    public int getIntervalSeconds() {
        return intervalSeconds;
    }

    public void setIntervalSeconds(int intervalSeconds) {
        this.intervalSeconds = intervalSeconds;
    }

    public int getFanOut() {
        return fanOut;
    }

    public void setFanOut(int fanOut) {
        this.fanOut = fanOut;
    }

    public int getAntiEntropyIntervalMinutes() {
        return antiEntropyIntervalMinutes;
    }

    public void setAntiEntropyIntervalMinutes(int antiEntropyIntervalMinutes) {
        this.antiEntropyIntervalMinutes = antiEntropyIntervalMinutes;
    }
}
