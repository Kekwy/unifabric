package com.kekwy.unifabric.adapter.control;

import com.kekwy.unifabric.adapter.config.AdapterIdentity;
import com.kekwy.unifabric.adapter.config.AdapterProperties;
import com.kekwy.unifabric.adapter.registry.DelegatingObserver;
import com.kekwy.unifabric.adapter.registry.AdapterRegistryClient;
import com.kekwy.unifabric.proto.fabric.ProviderRegistryServiceGrpc;
import com.kekwy.unifabric.proto.provider.ControlEnvelope;
import com.kekwy.unifabric.proto.provider.ProviderHeartbeat;
import com.kekwy.unifabric.proto.provider.ProviderHeartbeatAck;
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
 * 管理 ControlChannel 生命周期与心跳；asyncStub、scheduler、tags、providerId 均由 Spring 管理，
 * providerId 从 {@link AdapterIdentity} 读取。
 */
@Service
public class ControlService {

    private static final Logger log = LoggerFactory.getLogger(ControlService.class);
    private static final long RECONNECT_DELAY_SECONDS = 5;
    private static final long HEARTBEAT_INTERVAL_SECONDS = 30;

    private final ProviderRegistryServiceGrpc.ProviderRegistryServiceStub asyncStub;
    private final ScheduledExecutorService scheduler;
    private final List<String> tags;
    private final AdapterIdentity identity;

    private volatile boolean closed = false;
    private volatile ScheduledFuture<?> heartbeatTask;
    /** 当前 Control 流发送端，重连前需 onCompleted 关闭，避免多流并存 */
    private volatile StreamObserver<ControlEnvelope> controlSender;

    public ControlService(ProviderRegistryServiceGrpc.ProviderRegistryServiceStub asyncStub,
                          ScheduledExecutorService adapterScheduler,
                          AdapterProperties props,
                          AdapterIdentity identity) {
        this.asyncStub = asyncStub;
        this.scheduler = adapterScheduler;
        this.tags = props.getTags() != null ? props.getTags() : List.of();
        this.identity = identity;
    }

    /** 建立 Control 流（providerId 从 AdapterIdentity 读取，重连时同样）。重连前先关闭旧流，避免多流并存。 */
    public void openChannel() {
        String providerId = identity != null ? identity.getProviderId() : null;
        if (closed || asyncStub == null || providerId == null) return;

        StreamObserver<ControlEnvelope> prev = this.controlSender;
        this.controlSender = null;
        if (heartbeatTask != null) {
            heartbeatTask.cancel(false);
            heartbeatTask = null;
        }
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

        startHeartbeat(proxy);
        log.info("ControlChannel 已建立: providerId={}", providerId);
    }

    private void startHeartbeat(DelegatingObserver<ControlEnvelope> controlSender) {
        if (heartbeatTask != null) {
            heartbeatTask.cancel(false);
        }
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
        log.info("心跳已启动: 间隔={}s, providerId={}", HEARTBEAT_INTERVAL_SECONDS, identity != null ? identity.getProviderId() : null);
    }

    private void onDisconnect(StreamObserver<ControlEnvelope> disconnectedSender) {
        if (this.controlSender != disconnectedSender) {
            log.debug("忽略旧 ControlChannel 的断开事件");
            return;
        }
        this.controlSender = null;
        if (heartbeatTask != null) {
            heartbeatTask.cancel(false);
            heartbeatTask = null;
        }
        if (closed) return;
        log.warn("ControlChannel 断开，{}s 后重连...", RECONNECT_DELAY_SECONDS);
        scheduler.schedule(() -> {
            try { disconnectedSender.onCompleted(); } catch (Exception e) { log.trace("关闭旧流: {}", e.getMessage()); }
            openChannel();
        }, RECONNECT_DELAY_SECONDS, TimeUnit.SECONDS);
    }

    public void close() {
        closed = true;
        if (heartbeatTask != null) {
            heartbeatTask.cancel(false);
            heartbeatTask = null;
        }
    }

    // --- 业务方法，供 ControlMessageDispatcher 委托调用 ---

    public void handleHeartbeatAck(ProviderHeartbeatAck ack, String messageId) {
        if (ack != null && !ack.getAcknowledged()) {
            log.warn("心跳未确认: messageId={}", messageId);
        }
    }
}
