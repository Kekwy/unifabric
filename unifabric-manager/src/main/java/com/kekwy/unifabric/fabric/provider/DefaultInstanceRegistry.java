package com.kekwy.unifabric.fabric.provider;

import com.kekwy.unifabric.proto.provider.InstanceStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class DefaultInstanceRegistry implements InstanceRegistry {

    private static final Logger log = LoggerFactory.getLogger(DefaultInstanceRegistry.class);

    private final ConcurrentHashMap<String, InstanceRecord> instances = new ConcurrentHashMap<>();
    private final List<InstanceLifecycleListener> listeners = new CopyOnWriteArrayList<>();
    private volatile DeploymentTracker deploymentTracker;

    @Autowired(required = false)
    @Lazy
    public void setDeploymentTracker(DeploymentTracker deploymentTracker) {
        this.deploymentTracker = deploymentTracker;
    }

    @Override
    public void trackInstance(String instanceId, String providerId) {
        if (instanceId == null || instanceId.isBlank()) {
            return;
        }
        instances.computeIfAbsent(instanceId,
                id -> new InstanceRecord(id, providerId, InstanceStatus.PENDING));
        log.debug("实例已登记: instanceId={}, providerId={}", instanceId, providerId);
    }

    @Override
    public void updateStatus(String instanceId, String providerId,
                             InstanceStatus previous, InstanceStatus current, String message) {
        if (instanceId == null || instanceId.isBlank()) {
            return;
        }
        InstanceRecord record = instances.compute(instanceId, (id, existing) -> {
            if (existing == null) {
                InstanceRecord r = new InstanceRecord(id, providerId, current);
                r.setMessage(message);
                return r;
            }
            existing.setStatus(current);
            existing.setMessage(message != null ? message : "");
            return existing;
        });
        if (record == null) {
            return;
        }
        InstanceStatus prev = previous != null ? previous : InstanceStatus.INSTANCE_STATUS_UNSPECIFIED;
        InstanceStatus curr = current != null ? current : InstanceStatus.INSTANCE_STATUS_UNSPECIFIED;
        for (InstanceLifecycleListener listener : listeners) {
            try {
                listener.onInstanceStatusChanged(instanceId, providerId, prev, curr, message);
            } catch (Exception e) {
                log.warn("InstanceLifecycleListener 异常: instanceId={}", instanceId, e);
            }
        }
        DeploymentTracker tracker = deploymentTracker;
        if (tracker != null) {
            tracker.onInstanceStatusUpdate(instanceId, curr);
        }
    }

    @Override
    public void updateLocalEndpoint(String instanceId, String providerId, String host, int port) {
        if (instanceId == null || instanceId.isBlank()) {
            return;
        }
        instances.compute(instanceId, (id, existing) -> {
            InstanceRecord r = existing != null
                    ? existing
                    : new InstanceRecord(id, providerId != null ? providerId : "", InstanceStatus.PENDING);
            r.setLocalEndpoint(host, port);
            return r;
        });
        log.debug("实例端点已更新: instanceId={}, {}:{}", instanceId, host, port);
    }

    @Override
    public Optional<InstanceRecord> getRecord(String instanceId) {
        return Optional.ofNullable(instances.get(instanceId));
    }

    @Override
    public void addListener(InstanceLifecycleListener listener) {
        if (listener != null) {
            listeners.add(listener);
        }
    }
}
