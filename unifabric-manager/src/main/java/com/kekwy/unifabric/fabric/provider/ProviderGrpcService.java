package com.kekwy.unifabric.fabric.provider;

import com.kekwy.unifabric.fabric.connectivity.ConnectivityCoordinator;
import com.kekwy.unifabric.proto.fabric.ProviderRegistryServiceGrpc;
import com.kekwy.unifabric.proto.provider.*;
import io.grpc.*;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * {@code ProviderRegistryService} 的 gRPC 实现（位于 fabric 模块）。
 * <p>
 * 流式 RPC 通过 gRPC metadata 中的 {@code provider-id} 标识连接所属 Provider，
 * 由 {@link ProviderIdInterceptor} 提取到 gRPC Context 中。
 */
@Component
public class ProviderGrpcService
        extends ProviderRegistryServiceGrpc.ProviderRegistryServiceImplBase {

    private static final Logger log = LoggerFactory.getLogger(ProviderGrpcService.class);

    static final Metadata.Key<String> PROVIDER_ID_METADATA_KEY =
            Metadata.Key.of("provider-id", Metadata.ASCII_STRING_MARSHALLER);
    static final Context.Key<String> PROVIDER_ID_CTX_KEY = Context.key("provider-id");

    private final ProviderRegistry providerRegistry;
    private final InstanceRegistry instanceRegistry;
    private final ConnectivityCoordinator connectivityCoordinator;

    public ProviderGrpcService(ProviderRegistry providerRegistry,
                               InstanceRegistry instanceRegistry,
                               ConnectivityCoordinator connectivityCoordinator) {
        this.providerRegistry = providerRegistry;
        this.instanceRegistry = instanceRegistry;
        this.connectivityCoordinator = connectivityCoordinator;
    }

    @Override
    public void registerProvider(RegisterProviderRequest request,
                                 StreamObserver<RegisterProviderResponse> responseObserver) {
        log.info("收到 Provider 注册请求: name={}, type={}",
                request.getProviderName(), request.getProviderType());
        try {
            String providerId = providerRegistry.register(request);
            responseObserver.onNext(RegisterProviderResponse.newBuilder()
                    .setAccepted(true)
                    .setProviderId(providerId)
                    .setMessage("注册成功")
                    .build());
            responseObserver.onCompleted();
        } catch (Exception e) {
            log.error("Provider 注册失败: name={}", request.getProviderName(), e);
            responseObserver.onNext(RegisterProviderResponse.newBuilder()
                    .setAccepted(false)
                    .setMessage("注册失败: " + e.getMessage())
                    .build());
            responseObserver.onCompleted();
        }
    }

    @Override
    public StreamObserver<ControlEnvelope> controlChannel(StreamObserver<ControlEnvelope> responseObserver) {
        String providerId = PROVIDER_ID_CTX_KEY.get();
        if (providerId == null || providerId.isEmpty()) {
            log.error("ControlChannel 缺少 provider-id metadata");
            responseObserver.onError(Status.UNAUTHENTICATED
                    .withDescription("缺少 provider-id metadata header")
                    .asRuntimeException());
            return new NoOpStreamObserver<>();
        }

        log.info("Provider 建立 ControlChannel: providerId={}", providerId);
        providerRegistry.openControlChannel(providerId, responseObserver);

        return new StreamObserver<>() {
            @Override
            public void onNext(ControlEnvelope value) {
                switch (value.getMessageCase()) {
                    case PROVIDER_HEARTBEAT -> {
                        ProviderHeartbeat hb = value.getProviderHeartbeat();
                        providerRegistry.heartbeat(providerId);
                        providerRegistry.updateTags(providerId, hb.getTagsList());
                        ControlEnvelope ack = ControlEnvelope.newBuilder()
                                .setProviderHeartbeatAck(
                                        ProviderHeartbeatAck.newBuilder().setAcknowledged(true).build())
                                .build();
                        try {
                            responseObserver.onNext(ack);
                        } catch (Exception e) {
                            log.warn("回写心跳 Ack 失败: providerId={}", providerId, e);
                        }
                    }
                    case RESOURCE_CAPACITY_REPORT -> {
                        ResourceCapacityReport report = value.getResourceCapacityReport();
                        if (report.hasCapacity()) {
                            providerRegistry.updateResourceCapacity(providerId, report.getCapacity());
                        }
                        ControlEnvelope ack = ControlEnvelope.newBuilder()
                                .setResourceCapacityReportAck(
                                        ResourceCapacityReportAck.newBuilder().setAcknowledged(true).build())
                                .build();
                        try {
                            responseObserver.onNext(ack);
                        } catch (Exception e) {
                            log.warn("回写资源上报 Ack 失败: providerId={}", providerId, e);
                        }
                    }
                    default -> log.debug("ControlChannel 收到消息: providerId={}, messageCase={}",
                            providerId, value.getMessageCase());
                }
            }

            @Override
            public void onError(Throwable t) {
                log.warn("ControlChannel 异常断开: providerId={}, reason={}", providerId, t.getMessage());
                providerRegistry.closeControlChannel(providerId, responseObserver);
            }

            @Override
            public void onCompleted() {
                log.info("ControlChannel 由 Provider 正常关闭: providerId={}", providerId);
                providerRegistry.closeControlChannel(providerId, responseObserver);
                try {
                    responseObserver.onCompleted();
                } catch (Exception e) {
                    log.debug("responseObserver.onCompleted() 异常: {}", e.getMessage());
                }
            }
        };
    }

    @Override
    public StreamObserver<DeploymentEnvelope> deploymentChannel(StreamObserver<DeploymentEnvelope> responseObserver) {
        String providerId = PROVIDER_ID_CTX_KEY.get();
        if (providerId == null || providerId.isEmpty()) {
            log.error("DeploymentChannel 缺少 provider-id metadata");
            responseObserver.onError(Status.UNAUTHENTICATED
                    .withDescription("缺少 provider-id metadata header")
                    .asRuntimeException());
            return new NoOpStreamObserver<>();
        }

        log.info("Provider 建立 DeploymentChannel: providerId={}", providerId);
        providerRegistry.openDeploymentChannel(providerId, responseObserver);

        final StreamObserver<DeploymentEnvelope> thisSender = responseObserver;
        return new StreamObserver<>() {
            @Override
            public void onNext(DeploymentEnvelope response) {
                providerRegistry.handleDeploymentResponse(providerId, response);
            }

            @Override
            public void onError(Throwable t) {
                log.warn("DeploymentChannel 异常断开: providerId={}, reason={}", providerId, t.getMessage());
                providerRegistry.closeDeploymentChannel(providerId, thisSender);
            }

            @Override
            public void onCompleted() {
                log.info("DeploymentChannel 由 Provider 正常关闭: providerId={}", providerId);
                providerRegistry.closeDeploymentChannel(providerId, thisSender);
            }
        };
    }

    @Override
    public StreamObserver<SignalingEnvelope> signalingChannel(StreamObserver<SignalingEnvelope> responseObserver) {
        String providerId = PROVIDER_ID_CTX_KEY.get();
        if (providerId == null || providerId.isEmpty()) {
            log.error("SignalingChannel 缺少 provider-id metadata");
            responseObserver.onError(Status.UNAUTHENTICATED
                    .withDescription("缺少 provider-id metadata header")
                    .asRuntimeException());
            return new NoOpStreamObserver<>();
        }

        log.info("Provider 建立 SignalingChannel: providerId={}", providerId);
        providerRegistry.openSignalingChannel(providerId, responseObserver);

        final StreamObserver<SignalingEnvelope> thisSender = responseObserver;
        return new StreamObserver<>() {
            @Override
            public void onNext(SignalingEnvelope value) {
                var payloadCase = value.getPayloadCase();
                log.debug("SignalingChannel 收到: providerId={}, payloadCase={}", providerId, payloadCase);
                try {
                    switch (payloadCase) {
                        case INSTANCE_ENDPOINT_REPORT -> connectivityCoordinator.onInstanceEndpointReport(
                                providerId, value.getInstanceEndpointReport());
                        case RESOLVE_ENDPOINT_REQUEST -> connectivityCoordinator.onResolveEndpointRequest(
                                providerId, value.getResolveEndpointRequest());
                        case CANDIDATE_UPDATE -> connectivityCoordinator.onCandidateUpdate(
                                providerId, value.getCandidateUpdate());
                        case ICE_ENVELOPE -> connectivityCoordinator.onIceEnvelope(
                                providerId, value.getIceEnvelope());
                        case RESOLVE_ENDPOINT_RESPONSE -> log.debug(
                                "SignalingChannel 收到 ResolveEndpointResponse（通常由管理节点下发）: providerId={}",
                                providerId);
                        case INSTANCE_STATUS_CHANGED -> {
                            InstanceStatusChanged ch = value.getInstanceStatusChanged();
                            log.info("实例状态变更: providerId={}, instanceId={}, {} -> {}",
                                    providerId, ch.getInstanceId(), ch.getPreviousStatus(), ch.getCurrentStatus());
                            instanceRegistry.updateStatus(
                                    ch.getInstanceId(),
                                    providerId,
                                    ch.getPreviousStatus(),
                                    ch.getCurrentStatus(),
                                    ch.getMessage());
                        }
                        case PAYLOAD_NOT_SET -> log.warn("SignalingChannel 收到空 payload: providerId={}", providerId);
                        default -> log.debug("SignalingChannel 未专门处理: providerId={}, payloadCase={}",
                                providerId, payloadCase);
                    }
                } catch (Exception e) {
                    log.error("SignalingChannel 处理消息异常: providerId={}, payloadCase={}",
                            providerId, payloadCase, e);
                }
            }

            @Override
            public void onError(Throwable t) {
                log.warn("SignalingChannel 异常: providerId={}, reason={}", providerId, t.getMessage());
                providerRegistry.closeSignalingChannel(providerId, thisSender);
            }

            @Override
            public void onCompleted() {
                log.info("SignalingChannel 由 Provider 正常关闭: providerId={}", providerId);
                providerRegistry.closeSignalingChannel(providerId, thisSender);
                try {
                    responseObserver.onCompleted();
                } catch (Exception e) {
                    log.debug("responseObserver.onCompleted() 异常: {}", e.getMessage());
                }
            }
        };
    }

    public static class ProviderIdInterceptor implements ServerInterceptor {
        @Override
        public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
                ServerCall<ReqT, RespT> call, Metadata headers,
                ServerCallHandler<ReqT, RespT> next) {
            String providerId = headers.get(PROVIDER_ID_METADATA_KEY);
            if (providerId != null) {
                Context ctx = Context.current().withValue(PROVIDER_ID_CTX_KEY, providerId);
                return Contexts.interceptCall(ctx, call, headers, next);
            }
            return next.startCall(call, headers);
        }
    }

    private static class NoOpStreamObserver<T> implements StreamObserver<T> {
        @Override
        public void onNext(T value) { }

        @Override
        public void onError(Throwable t) { }

        @Override
        public void onCompleted() { }
    }
}
