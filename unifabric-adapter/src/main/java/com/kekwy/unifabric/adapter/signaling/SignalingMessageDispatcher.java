package com.kekwy.unifabric.adapter.signaling;

import com.kekwy.unifabric.proto.provider.SignalingEnvelope;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * SignalingChannel 消息分发：仅负责按 payloadCase 委托 SignalingService；ACTOR_READY 直接回传。
 */
public class SignalingMessageDispatcher implements StreamObserver<SignalingEnvelope> {

    private static final Logger log = LoggerFactory.getLogger(SignalingMessageDispatcher.class);

    private final SignalingService service;
    private final StreamObserver<SignalingEnvelope> responseObserver;
    private final Runnable onDisconnect;

    public SignalingMessageDispatcher(SignalingService service,
                                     StreamObserver<SignalingEnvelope> responseObserver,
                                     Runnable onDisconnect) {
        this.service = service;
        this.responseObserver = responseObserver;
        this.onDisconnect = onDisconnect;
    }

    @Override
    public void onNext(SignalingEnvelope value) {
        if (value == null) return;

        switch (value.getPayloadCase()) {
            case INSTANCE_MESSAGE_FORWARD:
                service.forwardEnvelopeToInstance(value.getInstanceMessageForward());
                break;
            case CONNECT_INSTRUCTION:
                service.handleConnectInstruction(value.getConnectInstruction());
                break;
            case ICE_ENVELOPE:
                service.handleIceEnvelope(value.getIceEnvelope());
                break;
            case INSTANCE_READY:
                responseObserver.onNext(value);
                break;
            case CANDIDATE_UPDATE:
            case INSTANCE_CHANNEL:
            default:
                log.debug("SignalingChannel 收到: {}", value.getPayloadCase());
        }
    }

    @Override
    public void onError(Throwable t) {
        log.warn("SignalingChannel 出错: {}", t.getMessage());
        if (onDisconnect != null) onDisconnect.run();
    }

    @Override
    public void onCompleted() {
        log.info("SignalingChannel 由服务端关闭");
        if (onDisconnect != null) onDisconnect.run();
    }
}
