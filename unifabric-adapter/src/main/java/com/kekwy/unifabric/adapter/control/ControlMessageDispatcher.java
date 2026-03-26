package com.kekwy.unifabric.adapter.control;

import com.kekwy.unifabric.proto.provider.ControlEnvelope;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * ControlChannel 消息分发：仅负责按 messageCase 委托 ControlService。
 */
public class ControlMessageDispatcher implements StreamObserver<ControlEnvelope> {

    private static final Logger log = LoggerFactory.getLogger(ControlMessageDispatcher.class);

    private final ControlService service;
    private final Runnable onDisconnect;

    public ControlMessageDispatcher(ControlService service, Runnable onDisconnect) {
        this.service = service;
        this.onDisconnect = onDisconnect;
    }

    @Override
    public void onNext(ControlEnvelope value) {
        if (value == null) return;
        switch (value.getMessageCase()) {
            case PROVIDER_HEARTBEAT_ACK:
                service.handleHeartbeatAck(value.getProviderHeartbeatAck(), value.getMessageId());
                break;
            case REGISTER_PROVIDER_REQUEST:
            case REGISTER_PROVIDER_RESPONSE:
                log.debug("ControlChannel 收到注册相关消息: {}", value.getMessageCase());
                break;
            default:
                log.debug("ControlChannel 收到: {}", value.getMessageCase());
        }
    }

    @Override
    public void onError(Throwable t) {
        log.warn("ControlChannel 出错: {}", t.getMessage());
        if (onDisconnect != null) onDisconnect.run();
    }

    @Override
    public void onCompleted() {
        log.info("ControlChannel 由服务端关闭");
        if (onDisconnect != null) onDisconnect.run();
    }
}
