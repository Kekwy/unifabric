package com.kekwy.unifabric.adapter.config;

import com.kekwy.unifabric.adapter.artifact.ArtifactFetcher;
import com.kekwy.unifabric.adapter.artifact.ArtifactStore;
import com.kekwy.unifabric.adapter.control.ControlService;
import com.kekwy.unifabric.adapter.deployment.DeploymentService;
import com.kekwy.unifabric.adapter.provider.ResourceProvider;
import com.kekwy.unifabric.adapter.provider.docker.DockerResourceProvider;
import com.kekwy.unifabric.adapter.provider.k8s.KubernetesResourceProvider;
import com.kekwy.unifabric.adapter.network.ProxyPortAllocator;
import com.kekwy.unifabric.adapter.network.TcpProxyServer;
import com.kekwy.unifabric.adapter.registry.AdapterRegistryClient;
import com.kekwy.unifabric.adapter.signaling.SignalingService;
import com.kekwy.unifabric.proto.fabric.ProviderRegistryServiceGrpc;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Path;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

/**
 * 资源适配器运行时 Bean 配置。
 */
@Configuration
public class AdapterRuntimeConfig {

    private static final Logger log = LoggerFactory.getLogger(AdapterRuntimeConfig.class);

    @Bean
    public ArtifactStore artifactStore(AdapterProperties props) {
        return new ArtifactStore(Path.of(props.getArtifactDir()));
    }

    @Bean(destroyMethod = "close")
    public ResourceProvider resourceProvider(AdapterProperties props, ArtifactStore artifactStore) {
        return createResourceProvider(props, artifactStore);
    }

    @Bean
    public ArtifactFetcher artifactFetcher(ArtifactStore artifactStore) {
        return new ArtifactFetcher(artifactStore);
    }

    @Bean(destroyMethod = "shutdown")
    public ManagedChannel controlPlaneChannel(AdapterProperties props) {
        var cp = props.getRegistry();
        return ManagedChannelBuilder.forAddress(cp.getHost(), cp.getPort()).usePlaintext().build();
    }

    @Bean
    public ProviderRegistryServiceGrpc.ProviderRegistryServiceBlockingStub providerRegistryBlockingStub(
            ManagedChannel controlPlaneChannel) {
        return ProviderRegistryServiceGrpc.newBlockingStub(controlPlaneChannel);
    }

    @Bean
    public ProviderRegistryServiceGrpc.ProviderRegistryServiceStub providerRegistryAsyncStub(
            ManagedChannel controlPlaneChannel) {
        return ProviderRegistryServiceGrpc.newStub(controlPlaneChannel);
    }

    @Bean(destroyMethod = "shutdownNow")
    public ScheduledExecutorService adapterScheduler() {
        return Executors.newScheduledThreadPool(2);
    }

    @Bean(name = "signalingIoExecutor", destroyMethod = "shutdown")
    public ExecutorService signalingIoExecutor() {
        return Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "adapter-signaling-io");
            t.setDaemon(true);
            return t;
        });
    }

    @Bean(destroyMethod = "close")
    public TcpProxyServer tcpProxyServer(AdapterProperties props) {
        AdapterProperties.Connectivity c = props.getConnectivity();
        ProxyPortAllocator alloc = null;
        if (c != null && c.getProxyPortMin() > 0 && c.getProxyPortMax() >= c.getProxyPortMin()) {
            alloc = new ProxyPortAllocator(c.getProxyPortMin(), c.getProxyPortMax());
        }
        return new TcpProxyServer(alloc);
    }

    @Bean(destroyMethod = "close")
    public AdapterRegistryClient adapterRegistryClient(AdapterProperties props,
                                                       AdapterIdentity adapterIdentity,
                                                       ProviderRegistryServiceGrpc.ProviderRegistryServiceBlockingStub providerRegistryBlockingStub,
                                                       ScheduledExecutorService adapterScheduler,
                                                       ControlService controlService,
                                                       DeploymentService deploymentService,
                                                       SignalingService signalingService) {
        var cp = props.getRegistry();
        AdapterRegistryClient client = new AdapterRegistryClient(
                providerRegistryBlockingStub,
                props,
                adapterIdentity,
                adapterScheduler,
                controlService,
                deploymentService,
                signalingService);
        client.start();
        log.info("资源适配器已连接控制平面: type={}, name={}, control-plane={}:{}",
                props.getType(), props.getName(), cp.getHost(), cp.getPort());
        return client;
    }

    private static ResourceProvider createResourceProvider(AdapterProperties props, ArtifactStore artifactStore) {
        return switch (props.getType().toLowerCase()) {
            case "docker" -> new DockerResourceProvider(
                    props.getDocker().getHost(),
                    props.getDocker().getNetwork(),
                    artifactStore);
            case "k8s", "kubernetes" -> new KubernetesResourceProvider(
                    props.getK8s().getKubeconfig(),
                    props.getK8s().isInCluster(),
                    props.getK8s().getNamespace(),
                    artifactStore);
            default -> throw new IllegalArgumentException(
                    "不支持的资源提供者类型: " + props.getType() + "，可选: docker, k8s");
        };
    }
}
