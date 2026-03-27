package com.kekwy.unifabric.adapter.control;

import com.kekwy.unifabric.adapter.config.AdapterIdentity;
import com.kekwy.unifabric.adapter.config.AdapterProperties;
import com.kekwy.unifabric.adapter.provider.ResourceProvider;
import com.kekwy.unifabric.adapter.registry.AdapterRegistryClient;
import com.kekwy.unifabric.adapter.registry.DelegatingObserver;
import com.kekwy.unifabric.adapter.util.ExponentialBackoff;
import com.kekwy.unifabric.proto.common.ResourceCapacity;
import com.kekwy.unifabric.proto.fabric.ProviderRegistryServiceGrpc;
import com.kekwy.unifabric.proto.provider.ControlEnvelope;
import com.kekwy.unifabric.proto.provider.ProviderHeartbeat;
import com.kekwy.unifabric.proto.provider.ProviderHeartbeatAck;
import com.kekwy.unifabric.proto.provider.ResourceCapacityReport;
import com.kekwy.unifabric.proto.provider.ResourceCapacityReportAck;
import io.grpc.Metadata;
import io.grpc.stub.MetadataUtils;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * 管理 ControlChannel：心跳与资源容量上报。
 */
@Service
public class ControlService {

    private static final Logger log = LoggerFactory.getLogger(ControlService.class);
    private static final long HEARTBEAT_INTERVAL_SECONDS = 30;
    private static final long RESOURCE_REPORT_INTERVAL_SECONDS = 60;

    private final ProviderRegistryServiceGrpc.ProviderRegistryServiceStub asyncStub;
    private final ScheduledExecutorService scheduler;
    private final List<String> tags;
    private final AdapterIdentity identity;
    private final ResourceProvider resourceProvider;

    private volatile boolean closed = false;
    private volatile ScheduledFuture<?> heartbeatTask;
    private volatile ScheduledFuture<?> resourceReportTask;
    private volatile StreamObserver<ControlEnvelope> controlSender;
    private final ExponentialBackoff reconnectBackoff = new ExponentialBackoff();

    public ControlService(ProviderRegistryServiceGrpc.ProviderRegistryServiceStub asyncStub,
                          ScheduledExecutorService adapterScheduler,
                          AdapterProperties props,
                          AdapterIdentity identity,
                          ResourceProvider resourceProvider) {
        this.asyncStub = asyncStub;
        this.scheduler = adapterScheduler;
        this.tags = props.getTags() != null ? props.getTags() : List.of();
        this.identity = identity;
        this.resourceProvider = resourceProvider;
    }

    public void openChannel() {
        String providerId = identity != null ? identity.getProviderId() : null;
        if (closed || asyncStub == null || providerId == null) return;

        StreamObserver<ControlEnvelope> prev = this.controlSender;
        this.controlSender = null;
        cancelPeriodicTasks();
        if (prev != null) {
            try { prev.onCompleted(); } catch (Exception e) { log.trace("关闭旧 Control 流: {}", e.getMessage()); }
        }

        DelegatingObserver<ControlEnvelope> proxy = new DelegatingObserver<>();
        final StreamObserver<ControlEnvelope> thisSender = proxy;
        StreamObserver<ControlEnvelope> receiver = new ControlMessageDispatcher(this,
                () -> onDisconnect(thisSender));

        Metadata headers = new Metadata();
        headers.put(AdapterRegistryClient.PROVIDER_ID_METADATA_KEY, providerId);
        var headerStub = asyncStub.withInterceptors(MetadataUtils.newAttachHeadersInterceptor(headers));

        StreamObserver<ControlEnvelope> sender = headerStub.controlChannel(receiver);
        proxy.setDelegate(sender);
        this.controlSender = proxy;

        startPeriodicTasks(proxy);
        reconnectBackoff.reset();
        log.info("ControlChannel 已建立: providerId={}", providerId);
    }

    private void cancelPeriodicTasks() {
        if (heartbeatTask != null) {
            heartbeatTask.cancel(false);
            heartbeatTask = null;
        }
        if (resourceReportTask != null) {
            resourceReportTask.cancel(false);
            resourceReportTask = null;
        }
    }

    private void startPeriodicTasks(DelegatingObserver<ControlEnvelope> controlSender) {
        heartbeatTask = scheduler.scheduleAtFixedRate(() -> {
            if (closed) return;
            String current = identity != null ? identity.getProviderId() : null;
            if (current == null) return;
            try {
                ControlEnvelope heartbeat = ControlEnvelope.newBuilder()
                        .setProviderHeartbeat(ProviderHeartbeat.newBuilder()
                                .setProviderId(current)
                                .setTimestampMs(System.currentTimeMillis())
                                .addAllTags(tags)
                                .build())
                        .build();
                controlSender.onNext(heartbeat);
            } catch (Exception e) {
                log.warn("发送心跳失败: providerId={}", current, e);
            }
        }, HEARTBEAT_INTERVAL_SECONDS, HEARTBEAT_INTERVAL_SECONDS, TimeUnit.SECONDS);

        resourceReportTask = scheduler.scheduleAtFixedRate(() -> {
            if (closed) return;
            String current = identity != null ? identity.getProviderId() : null;
            if (current == null) return;
            try {
                ResourceCapacity cap = resourceProvider.reportResourceCapacity();
                ControlEnvelope report = ControlEnvelope.newBuilder()
                        .setResourceCapacityReport(ResourceCapacityReport.newBuilder()
                                .setProviderId(current)
                                .setTimestampMs(System.currentTimeMillis())
                                .setCapacity(cap)
                                .build())
                        .build();
                controlSender.onNext(report);
            } catch (Exception e) {
                log.warn("发送资源容量上报失败: providerId={}", current, e);
            }
        }, RESOURCE_REPORT_INTERVAL_SECONDS, RESOURCE_REPORT_INTERVAL_SECONDS, TimeUnit.SECONDS);

        log.info("控制通道周期任务已启动: 心跳={}s, 资源上报={}s", HEARTBEAT_INTERVAL_SECONDS, RESOURCE_REPORT_INTERVAL_SECONDS);
    }

    private void onDisconnect(StreamObserver<ControlEnvelope> disconnectedSender) {
        if (this.controlSender != disconnectedSender) {
            log.debug("忽略旧 ControlChannel 的断开事件");
            return;
        }
        this.controlSender = null;
        cancelPeriodicTasks();
        if (closed) return;
        long delayMs = reconnectBackoff.nextDelayMs();
        log.warn("ControlChannel 断开，{}ms 后重连（指数退避）...", delayMs);
        scheduler.schedule(() -> {
            try { disconnectedSender.onCompleted(); } catch (Exception e) { log.trace("关闭旧流: {}", e.getMessage()); }
            openChannel();
        }, delayMs, TimeUnit.MILLISECONDS);
    }

    public void close() {
        closed = true;
        cancelPeriodicTasks();
    }

    public void handleHeartbeatAck(ProviderHeartbeatAck ack, String messageId) {
        if (ack != null && !ack.getAcknowledged()) {
            log.warn("心跳未确认: messageId={}", messageId);
        }
    }

    public void handleResourceCapacityReportAck(ResourceCapacityReportAck ack) {
        if (ack != null && !ack.getAcknowledged()) {
            log.warn("资源上报未确认");
        }
    }
}
