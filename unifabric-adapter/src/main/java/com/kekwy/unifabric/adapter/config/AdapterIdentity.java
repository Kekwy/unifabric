package com.kekwy.unifabric.adapter.config;

import org.springframework.stereotype.Component;

/**
 * 由 Spring 管理的当前资源适配器运行时身份：注册成功后写入 providerId，
 * Control / Deployment / Signaling 等从本 Bean 读取，无需在调用链中传递。
 */
@Component
public class AdapterIdentity {

    private volatile String providerId;

    public String getProviderId() {
        return providerId;
    }

    public void setProviderId(String providerId) {
        this.providerId = providerId;
    }

    public boolean isRegistered() {
        return providerId != null && !providerId.isBlank();
    }
}
