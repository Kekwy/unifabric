package com.kekwy.unifabric.fabric.config;

import com.kekwy.unifabric.fabric.discovery.DiscoveryGrpcService;
import com.kekwy.unifabric.fabric.provider.ProviderGrpcService;
import com.kekwy.unifabric.fabric.scheduling.SchedulingGrpcService;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.ServerInterceptors;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * 启动并维护 ProviderRegistryService 与 DiscoveryService 的 gRPC 服务端（含 provider-id 拦截器）。
 */
@Component
public class FabricGrpcServerLifecycle {

    private static final Logger log = LoggerFactory.getLogger(FabricGrpcServerLifecycle.class);

    private final ProviderGrpcService providerGrpcService;
    private final DiscoveryGrpcService discoveryGrpcService;
    private final SchedulingGrpcService schedulingGrpcService;
    private final int port;
    private Server server;

    public FabricGrpcServerLifecycle(ProviderGrpcService providerGrpcService,
                                     DiscoveryGrpcService discoveryGrpcService,
                                     SchedulingGrpcService schedulingGrpcService,
                                     @Value("${unifabric.fabric.grpc.port:9090}") int port) {
        this.providerGrpcService = providerGrpcService;
        this.discoveryGrpcService = discoveryGrpcService;
        this.schedulingGrpcService = schedulingGrpcService;
        this.port = port;
    }

    @PostConstruct
    public void start() throws IOException {
        server = ServerBuilder.forPort(port)
                .addService(ServerInterceptors.intercept(
                        providerGrpcService,
                        new ProviderGrpcService.ProviderIdInterceptor()))
                .addService(discoveryGrpcService)
                .addService(schedulingGrpcService)
                .build()
                .start();
        log.info("Fabric gRPC 服务已启动: port={}", port);
    }

    @PreDestroy
    public void stop() throws InterruptedException {
        if (server == null) {
            return;
        }
        server.shutdown();
        if (!server.awaitTermination(10, TimeUnit.SECONDS)) {
            server.shutdownNow();
        }
        log.info("Fabric gRPC 服务已关闭");
    }
}
