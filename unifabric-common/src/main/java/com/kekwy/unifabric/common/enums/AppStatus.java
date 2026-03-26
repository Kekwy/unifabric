package com.kekwy.unifabric.common.enums;

import lombok.Getter;

import java.util.Arrays;

@Getter
public enum AppStatus {

    APP_STATUS_IDLE(0, "idle"),
    APP_STATUS_STOPPED(1, "stopped"),
    APP_STATUS_DEPLOYING(2, "deploying"),
    APP_STATUS_RUNNING(3, "running"),
    APP_STATUS_FAILED(4, "failed"),
    ;

    public static AppStatus parse(String name) {
        return Arrays.stream(AppStatus.values())
            .filter(status -> status.getName().equals(name))
            .findFirst()
            .orElse(null);
    }

    private final int code;
    private final String name;

    AppStatus(int code, String name) {
        this.code = code;
        this.name = name;
    }
}
