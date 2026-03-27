package com.kekwy.unifabric.fabric.scheduling;

import com.kekwy.unifabric.proto.provider.InstanceInfo;

import java.util.Optional;

/**
 * 单次调度尝试结果。
 */
public final class SchedulingResult {

    private final boolean success;
    private final String targetProviderId;
    private final InstanceInfo instance;
    private final String errorMessage;

    private SchedulingResult(boolean success, String targetProviderId, InstanceInfo instance, String errorMessage) {
        this.success = success;
        this.targetProviderId = targetProviderId != null ? targetProviderId : "";
        this.instance = instance;
        this.errorMessage = errorMessage != null ? errorMessage : "";
    }

    public boolean isSuccess() {
        return success;
    }

    public String getTargetProviderId() {
        return targetProviderId;
    }

    public Optional<InstanceInfo> getInstance() {
        return Optional.ofNullable(instance);
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public static SchedulingResult ok(String targetProviderId, InstanceInfo instance) {
        return new SchedulingResult(true, targetProviderId, instance, "");
    }

    public static SchedulingResult failure(String message) {
        return new SchedulingResult(false, "", null, message);
    }

    public static SchedulingResult failure(String targetProviderId, String message) {
        return new SchedulingResult(false, targetProviderId, null, message);
    }
}
