package com.kekwy.unifabric.fabric.scheduling;

import java.util.concurrent.CompletableFuture;

/**
 * 多域协同调度入口（论文 3.4）。
 */
public interface SchedulingService {

    /**
     * 异步执行完整分级调度流程（域内优先、跨域升级、乐观记账与部署）。
     */
    CompletableFuture<SchedulingResult> schedule(DeployRequest request);

    /**
     * 当前线程同步执行（供队列 worker 与测试使用）。
     */
    SchedulingResult scheduleBlocking(DeployRequest request);
}
