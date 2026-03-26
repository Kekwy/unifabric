package com.kekwy.unifabric.adapter.actor;

import com.kekwy.unifabric.adapter.signaling.SignalingService;
import com.kekwy.unifabric.proto.actor.ActorEnvelope;
import com.kekwy.unifabric.proto.provider.ActorRegistrationServiceGrpc;
import io.grpc.stub.StreamObserver;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * 本地 Actor 注册与消息入口：实现 ActorRegistrationService，流使用 ActorEnvelope。
 * 处理 register_actor、row（DataRow），将消息交由 ActorRouter 按 target 路由；
 * 注册后通过 SignalingService 上报 Actor 就绪与通道建立。
 */
@Slf4j
@Component
public class ActorRegistrationServiceGrpcImpl extends ActorRegistrationServiceGrpc.ActorRegistrationServiceImplBase {

    private final ActorRouter router;
    private final SignalingService signalingService;

    public ActorRegistrationServiceGrpcImpl(ActorRouter router, SignalingService signalingService) {
        this.router = router;
        this.signalingService = signalingService;
    }

    @Override
    public StreamObserver<ActorEnvelope> actorChannel(StreamObserver<ActorEnvelope> responseObserver) {
        return new StreamObserver<>() {
            private volatile String registeredActorId;

            @Override
            public void onNext(ActorEnvelope msg) {
                if (msg == null) return;
                switch (msg.getPayloadCase()) {
                    case REGISTER_ACTOR:
                        registeredActorId = msg.getRegisterActor().getActorId();
                        Set<ActorEdge> establishedEdges = router.onActorConnected(registeredActorId, responseObserver);
                        signalingService.reportActorReady(registeredActorId);
                        for (ActorEdge edge : establishedEdges) {
                            signalingService.reportChannelEstablished(edge.fromActorId(), edge.toActorId());
                        }
                        break;
                    case RESPONSE:
                        if (registeredActorId != null) {
                            router.routeEnvelope(registeredActorId, msg);
                        } else {
                            log.warn("收到 row 但该连接尚未 register_actor");
                        }
                        break;
                    default:
                        log.debug("ActorChannel 收到: {}", msg.getPayloadCase());
                }
            }

            @Override
            public void onError(Throwable t) {
                if (registeredActorId != null) router.onActorLostConnection(registeredActorId);
                log.warn("ActorChannel 出错: {}", t.getMessage());
            }

            @Override
            public void onCompleted() {
                if (registeredActorId != null) router.onActorLostConnection(registeredActorId);
                responseObserver.onCompleted();
            }
        };
    }
}
