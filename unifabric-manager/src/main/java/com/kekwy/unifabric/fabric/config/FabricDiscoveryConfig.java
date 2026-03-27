package com.kekwy.unifabric.fabric.config;

import com.kekwy.unifabric.fabric.discovery.NodeDiscoveryManager;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * 域级节点发现与本机资源视图（论文 3.2.3）所需的 Bean。
 */
@Configuration
@EnableConfigurationProperties(FabricGossipProperties.class)
public class FabricDiscoveryConfig {

    @Bean
    public NodeDiscoveryManager nodeDiscoveryManager(
            @Value("${unifabric.fabric.node-id:fabric-local}") String nodeId,
            @Value("${unifabric.fabric.domain-id:default}") String domainId,
            @Value("${unifabric.fabric.node-ttl-minutes:5}") long nodeTtlMinutes,
            @Value("${unifabric.fabric.advertise-address:localhost}") String advertiseAddress) {
        return new NodeDiscoveryManager(nodeId, domainId, Duration.ofMinutes(nodeTtlMinutes), advertiseAddress);
    }
}
