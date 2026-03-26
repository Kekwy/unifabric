package com.kekwy.unifabric.fabric.provider;

import com.kekwy.unifabric.proto.fabric.ProviderRegistryServiceGrpc;
import com.kekwy.unifabric.proto.provider.*;
import com.kekwy.unifabric.fabric.actor.ActorRegistry;
import io.grpc.*;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * {@code ProviderRegistryService} 的 gRPC 实现（位于 fabric 模块）。
 * <p>
 * 实现全部 4 个 RPC：
 * <ul>
 *   <li>{@code RegisterProvider} — 一元 RPC，Provider 注册</li>
 *   <li>{@code ControlChannel} — 双向流，控制消息（预留）</li>
 *   <li>{@code DeploymentChannel} — 双向流，部署命令的请求-响应</li>
 *   <li>{@code SignalingChannel} — 双向流，实例状态信令</li>
 * </ul>
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
    private final ActorRegistry actorRegistry;

    public ProviderGrpcService(ProviderRegistry providerRegistry,
                               ActorRegistry actorRegistry) {
        this.providerRegistry = providerRegistry;
        this.actorRegistry = actorRegistry;
    }

    // ======================== RegisterProvider (unary) ========================

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

    // ======================== ControlChannel (bidi stream) ========================

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

        ProviderConnection conn = providerRegistry.getConnection(providerId);
        if (conn != null) {
            StreamObserver<ControlEnvelope> old = conn.getControlSender();
            if (old != null) {
                try { old.onCompleted(); } catch (Exception e) { log.trace("关闭旧 Control 流: {}", e.getMessage()); }
            }
            conn.setControlSender(responseObserver);
        }

        return new StreamObserver<>() {
            @Override
            public void onNext(ControlEnvelope value) {
                if (value.hasProviderHeartbeat()) {
                    providerRegistry.heartbeat(providerId);
                    ControlEnvelope ack = ControlEnvelope.newBuilder()
                            .setProviderHeartbeatAck(ProviderHeartbeatAck.newBuilder().setAcknowledged(true).build())
                            .build();
                    try {
                        responseObserver.onNext(ack);
                    } catch (Exception e) {
                        log.warn("回写心跳 Ack 失败: providerId={}", providerId, e);
                    }
                } else {
                    log.debug("ControlChannel 收到消息: providerId={}, messageCase={}",
                            providerId, value.getMessageCase());
                }
            }

            @Override
            public void onError(Throwable t) {
                log.warn("ControlChannel 异常断开: providerId={}, reason={}", providerId, t.getMessage());
            }

            @Override
            public void onCompleted() {
                log.info("ControlChannel 由 Provider 正常关闭: providerId={}", providerId);
                try {
                    responseObserver.onCompleted();
                } catch (Exception e) {
                    log.debug("responseObserver.onCompleted() 异常: {}", e.getMessage());
                }
            }
        };
    }

    // ======================== DeploymentChannel (bidi stream) ========================

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

    // ======================== SignalingChannel (bidi stream) ========================

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

        ProviderConnection conn = providerRegistry.getConnection(providerId);
        if (conn != null) {
            StreamObserver<SignalingEnvelope> old = conn.getSignalingSender();
            if (old != null) {
                try { old.onCompleted(); } catch (Exception e) { log.trace("关闭旧 Signaling 流: {}", e.getMessage()); }
            }
            conn.setSignalingSender(responseObserver);
        }

        return new StreamObserver<>() {
            @Override
            public void onNext(SignalingEnvelope value) {
                var payloadCase = value.getPayloadCase();
                log.info("SignalingChannel 收到: providerId={}, payloadCase={}", providerId, payloadCase);
                try {
                    switch (payloadCase) {
                        case INSTANCE_READY -> handleInstanceReady(providerId, value.getInstanceReady());
                        case INSTANCE_CHANNEL -> handleInstanceChannel(value.getInstanceChannel());
                        case PAYLOAD_NOT_SET -> log.warn("SignalingChannel 收到空 payload: providerId={}", providerId);
                        default -> log.debug("SignalingChannel 收到未处理的消息类型: providerId={}, type={}",
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
            }

            @Override
            public void onCompleted() {
                log.info("SignalingChannel 由 Provider 正常关闭: providerId={}", providerId);
                try {
                    responseObserver.onCompleted();
                } catch (Exception e) {
                    log.debug("responseObserver.onCompleted() 异常: {}", e.getMessage());
                }
            }
        };
    }

    // ======================== 信令事件处理 ========================

    private void handleInstanceReady(String providerId, InstanceReadyReport ready) {
        String instanceId = ready.getInstanceId();
        if (instanceId == null || instanceId.isBlank()) {
            log.warn("收到 InstanceReadyReport 但 instance_id 为空");
            return;
        }
        log.info("收到 InstanceReadyReport: instanceId={}, providerId={}", instanceId, providerId);
        actorRegistry.onActorReady(instanceId, providerId);
    }

    private void handleInstanceChannel(InstanceChannelStatus status) {
        String src = status.getSrcInstanceAddr();
        String dst = status.getDstInstanceAddr();
        log.info("收到 InstanceChannelStatus: src={}, dst={}, connected={}", src, dst, status.getConnected());

        if (status.getConnected()) {
            actorRegistry.onChannelConnected(src, dst);
        }
    }

    // ======================== 内部类 ========================

    /**
     * 从 gRPC metadata 提取 {@code provider-id} 并注入 Context。
     * <p>
     * 在构建 gRPC Server 时通过
     * {@code ServerInterceptors.intercept(service, new ProviderIdInterceptor())}
     * 显式包装本服务。
     */
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

    /**
     * 空操作的 StreamObserver，用于错误场景下的占位返回。
     */
    private static class NoOpStreamObserver<T> implements StreamObserver<T> {
        @Override
        public void onNext(T value) { }

        @Override
        public void onError(Throwable t) { }

        @Override
        public void onCompleted() { }
    }
}
