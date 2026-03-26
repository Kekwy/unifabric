package com.kekwy.unifabric.adapter;

import com.kekwy.unifabric.adapter.config.AdapterProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * 资源适配器启动入口。
 * <p>
 * Actor 注册 gRPC Server、AdapterRegistryClient、ResourceEngine 等由
 * {@link com.kekwy.unifabric.adapter.config.AdapterRuntimeConfig} 提供并纳入 Spring 管理。
 */
@SpringBootApplication
@EnableConfigurationProperties(AdapterProperties.class)
public class AdapterApplication {

    public static void main(String[] args) {
        SpringApplication.run(AdapterApplication.class, args);
    }
}
