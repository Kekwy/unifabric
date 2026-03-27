package com.kekwy.unifabric.fabric.scheduling;

import com.kekwy.unifabric.proto.fabric.ScheduleDeployRequest;
import com.kekwy.unifabric.proto.fabric.ScheduleDeployResponse;
import com.kekwy.unifabric.proto.fabric.SchedulingServiceGrpc;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.StatusRuntimeException;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * 向远程管理节点发起 {@link SchedulingServiceGrpc} 调用（论文 3.4.2 跨域升级）。
 */
@Component
public class CrossDomainSchedulingClient {

    private static final Logger log = LoggerFactory.getLogger(CrossDomainSchedulingClient.class);

    private final ConcurrentHashMap<String, ManagedChannel> channels = new ConcurrentHashMap<>();
    private final SchedulingProperties schedulingProperties;

    public CrossDomainSchedulingClient(SchedulingProperties schedulingProperties) {
        this.schedulingProperties = schedulingProperties;
    }

    public ScheduleDeployResponse scheduleDeploy(String targetAddress, ScheduleDeployRequest request) {
        String target = normalizeAddress(targetAddress);
        if (target.isEmpty()) {
            return ScheduleDeployResponse.newBuilder()
                    .setSuccess(false)
                    .setErrorMessage("empty target address")
                    .build();
        }
        ManagedChannel channel = channels.computeIfAbsent(target, this::openChannel);
        if (channel.isShutdown()) {
            channels.remove(target, channel);
            channel = channels.computeIfAbsent(target, this::openChannel);
        }
        try {
            SchedulingServiceGrpc.SchedulingServiceBlockingStub stub =
                    SchedulingServiceGrpc.newBlockingStub(channel)
                            .withDeadlineAfter(schedulingProperties.getDeployTimeoutSeconds() * 2L,
                                    TimeUnit.SECONDS);
            return stub.scheduleDeploy(request);
        } catch (StatusRuntimeException e) {
            log.debug("跨域 ScheduleDeploy 失败 target={}: {}", target, e.getStatus());
            channel.shutdown();
            channels.remove(target, channel);
            return ScheduleDeployResponse.newBuilder()
                    .setSuccess(false)
                    .setErrorMessage(e.getStatus().toString())
                    .build();
        }
    }

    private ManagedChannel openChannel(String target) {
        return ManagedChannelBuilder.forTarget(target)
                .usePlaintext()
                .keepAliveTime(30, TimeUnit.SECONDS)
                .keepAliveTimeout(10, TimeUnit.SECONDS)
                .build();
    }

    private static String normalizeAddress(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.trim();
    }

    @PreDestroy
    public void shutdown() {
        channels.values().forEach(ch -> {
            try {
                ch.shutdown();
                if (!ch.awaitTermination(5, TimeUnit.SECONDS)) {
                    ch.shutdownNow();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                ch.shutdownNow();
            }
        });
        channels.clear();
    }
}
