package com.kekwy.unifabric.adapter.signaling;

import com.kekwy.unifabric.proto.provider.SignalingEnvelope;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * SignalingChannel 入站消息分发。
 */
public class SignalingMessageDispatcher implements StreamObserver<SignalingEnvelope> {

    private static final Logger log = LoggerFactory.getLogger(SignalingMessageDispatcher.class);

    private final SignalingService service;
    private final Runnable onDisconnect;

    public SignalingMessageDispatcher(SignalingService service, Runnable onDisconnect) {
        this.service = service;
        this.onDisconnect = onDisconnect;
    }

    @Override
    public void onNext(SignalingEnvelope value) {
        if (value == null) return;

        switch (value.getPayloadCase()) {
            case CONNECT_INSTRUCTION:
                service.handleConnectInstruction(value.getConnectInstruction());
                break;
            case ICE_ENVELOPE:
                service.handleIceEnvelope(value.getIceEnvelope());
                break;
            case CANDIDATE_UPDATE:
                service.handleCandidateUpdate(value.getCandidateUpdate());
                break;
            case RESOLVE_ENDPOINT_RESPONSE:
                service.handleResolveEndpointResponse(value.getResolveEndpointResponse());
                break;
            case RESOLVE_ENDPOINT_REQUEST:
            case INSTANCE_ENDPOINT_REPORT:
            case INSTANCE_STATUS_CHANGED:
                log.debug("SignalingChannel 收到（通常为本端上行类型）: {}", value.getPayloadCase());
                break;
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
