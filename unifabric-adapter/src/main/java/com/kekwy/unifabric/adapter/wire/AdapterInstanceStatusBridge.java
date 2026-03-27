package com.kekwy.unifabric.adapter.wire;

import com.kekwy.unifabric.adapter.provider.ResourceProvider;
import com.kekwy.unifabric.adapter.signaling.SignalingService;
import org.springframework.stereotype.Component;

/**
 * 将 {@link ResourceProvider} 的容器事件回调接到 {@link SignalingService}（论文 3.2.4）。
 */
@Component
public class AdapterInstanceStatusBridge {

    public AdapterInstanceStatusBridge(ResourceProvider resourceProvider, SignalingService signalingService) {
        resourceProvider.setInstanceStatusListener(
                (instanceId, previous, current, message) ->
                        signalingService.reportInstanceStatusChanged(instanceId, previous, current, message));
    }
}
