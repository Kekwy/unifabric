package com.kekwy.unifabric.fabric.config;

import com.kekwy.unifabric.fabric.scheduling.SchedulingProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 调度相关配置 Bean（{@link SchedulingProperties}）。
 */
@Configuration
@EnableConfigurationProperties(SchedulingProperties.class)
public class SchedulingConfig {
}
