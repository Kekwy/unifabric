package com.kekwy.unifabric.fabric.scheduling;

import org.springframework.context.ApplicationEvent;

/**
 * 域内 Provider 或跨域 Gossip 视图发生变化时发布，用于唤醒退避队列（论文 3.4.2 事件驱动触发）。
 */
public class SchedulingTopologyChangedEvent extends ApplicationEvent {

    public SchedulingTopologyChangedEvent(Object source) {
        super(source);
    }
}
