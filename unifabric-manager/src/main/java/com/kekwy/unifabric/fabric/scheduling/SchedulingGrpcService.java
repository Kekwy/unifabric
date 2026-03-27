package com.kekwy.unifabric.fabric.scheduling;

import com.kekwy.unifabric.proto.fabric.ScheduleDeployRequest;
import com.kekwy.unifabric.proto.fabric.ScheduleDeployResponse;
import com.kekwy.unifabric.proto.fabric.SchedulingServiceGrpc;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * 跨域调度 gRPC 入口：远程域将请求交由本域 {@link DefaultSchedulingService} 处理（论文 3.4.2）。
 */
@Component
public class SchedulingGrpcService extends SchedulingServiceGrpc.SchedulingServiceImplBase {

    private static final Logger log = LoggerFactory.getLogger(SchedulingGrpcService.class);

    private final DefaultSchedulingService schedulingService;

    public SchedulingGrpcService(DefaultSchedulingService schedulingService) {
        this.schedulingService = schedulingService;
    }

    @Override
    public void scheduleDeploy(ScheduleDeployRequest request,
                               StreamObserver<ScheduleDeployResponse> responseObserver) {
        try {
            DeployRequest dr = fromProto(request);
            schedulingService.schedule(dr).whenComplete((res, ex) -> {
                try {
                    if (ex != null) {
                        log.warn("ScheduleDeploy 异步失败 requestId={}: {}", dr.getRequestId(), ex.toString());
                        responseObserver.onNext(ScheduleDeployResponse.newBuilder()
                                .setSuccess(false)
                                .setErrorMessage(ex.getMessage() != null ? ex.getMessage() : ex.toString())
                                .build());
                    } else {
                        responseObserver.onNext(toProto(res));
                    }
                    responseObserver.onCompleted();
                } catch (Exception e) {
                    responseObserver.onError(e);
                }
            });
        } catch (Exception e) {
            log.error("ScheduleDeploy 参数处理失败", e);
            responseObserver.onNext(ScheduleDeployResponse.newBuilder()
                    .setSuccess(false)
                    .setErrorMessage(e.getMessage() != null ? e.getMessage() : e.toString())
                    .build());
            responseObserver.onCompleted();
        }
    }

    static DeployRequest fromProto(ScheduleDeployRequest req) {
        String rid = req.getRequestId();
        if (rid == null || rid.isBlank()) {
            rid = UUID.randomUUID().toString();
        }
        DeployRequest.Builder b = DeployRequest.builder()
                .requestId(rid)
                .demand(req.getDemand())
                .image(req.getImage())
                .env(req.getEnvMap())
                .labels(req.getLabelsMap())
                .artifactUrl(req.getArtifactUrl())
                .originatingDomainId(req.getOriginatingDomainId());
        if (req.hasConstraints()) {
            b.providerTypeConstraint(req.getConstraints().getProviderType());
            b.requiredTags(req.getConstraints().getRequiredTagsList());
        }
        return b.build();
    }

    static ScheduleDeployResponse toProto(SchedulingResult res) {
        ScheduleDeployResponse.Builder b = ScheduleDeployResponse.newBuilder()
                .setSuccess(res.isSuccess())
                .setTargetProviderId(res.getTargetProviderId())
                .setErrorMessage(res.getErrorMessage());
        res.getInstance().ifPresent(b::setInstance);
        return b.build();
    }
}
