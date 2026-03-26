package com.kekwy.unifabric.adapter.deployment;

import com.kekwy.unifabric.proto.provider.*;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * DeploymentChannel 消息分发：仅负责按 messageCase 委托 DeploymentService，并封装响应信封。
 */
public class DeploymentMessageDispatcher implements StreamObserver<DeploymentEnvelope> {

    private static final Logger log = LoggerFactory.getLogger(DeploymentMessageDispatcher.class);

    private final DeploymentService service;
    private final StreamObserver<DeploymentEnvelope> responseObserver;
    private final Runnable onDisconnect;

    public DeploymentMessageDispatcher(DeploymentService service,
                                       StreamObserver<DeploymentEnvelope> responseObserver,
                                       Runnable onDisconnect) {
        this.service = service;
        this.responseObserver = responseObserver;
        this.onDisconnect = onDisconnect;
    }

    @Override
    public void onNext(DeploymentEnvelope envelope) {
        String correlationId = envelope.getCorrelationId();
        String messageId = envelope.getMessageId();

        try {
            DeploymentEnvelope response = dispatch(envelope);
            if (response != null) {
                responseObserver.onNext(response);
            }
        } catch (Exception e) {
            log.error("处理部署消息失败: messageId={}, correlationId={}", messageId, correlationId, e);
        }
    }

    @Override
    public void onError(Throwable t) {
        log.warn("DeploymentChannel 出错: {}", t.getMessage());
        if (onDisconnect != null) onDisconnect.run();
    }

    @Override
    public void onCompleted() {
        log.info("DeploymentChannel 由服务端关闭");
        if (onDisconnect != null) onDisconnect.run();
    }

    private DeploymentEnvelope dispatch(DeploymentEnvelope envelope) throws IOException {
        String messageId = envelope.getMessageId();
        // Fabric 端按 response.correlationId 匹配请求，期望为请求的 messageId
        String correlationId = (messageId != null && !messageId.isEmpty()) ? messageId : envelope.getCorrelationId();

        switch (envelope.getMessageCase()) {
            case DEPLOY_INSTANCE_REQUEST:
                log.info("收到部署请求: messageId={}, instanceId={}", messageId, envelope.getDeployInstanceRequest().getInstanceId());
                DeployInstanceResponse deployResp = service.deployInstance(envelope.getDeployInstanceRequest());
                return DeploymentEnvelope.newBuilder()
                        .setCorrelationId(correlationId)
                        .setMessageId(messageId)
                        .setDeployInstanceResponse(deployResp)
                        .build();

            case STOP_INSTANCE_REQUEST:
                StopInstanceResponse stopResp = service.stopInstance(envelope.getStopInstanceRequest());
                return DeploymentEnvelope.newBuilder()
                        .setCorrelationId(correlationId)
                        .setMessageId(messageId)
                        .setStopInstanceResponse(stopResp)
                        .build();

            case REMOVE_INSTANCE_REQUEST:
                RemoveInstanceResponse removeResp = service.removeInstance(envelope.getRemoveInstanceRequest());
                return DeploymentEnvelope.newBuilder()
                        .setCorrelationId(correlationId)
                        .setMessageId(messageId)
                        .setRemoveInstanceResponse(removeResp)
                        .build();

            case GET_INSTANCE_STATUS_REQUEST:
                GetInstanceStatusResponse statusResp = service.getInstanceStatus(envelope.getGetInstanceStatusRequest());
                return DeploymentEnvelope.newBuilder()
                        .setCorrelationId(correlationId)
                        .setMessageId(messageId)
                        .setGetInstanceStatusResponse(statusResp)
                        .build();

            default:
                log.debug("忽略 DeploymentEnvelope: {}", envelope.getMessageCase());
                return null;
        }
    }
}
