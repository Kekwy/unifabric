package com.kekwy.unifabric.adapter.engine.docker;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.api.model.*;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient;
import com.github.dockerjava.transport.DockerHttpClient;
import com.kekwy.unifabric.adapter.artifact.ArtifactStore;
import com.kekwy.unifabric.adapter.engine.ResourceProvider;
import com.kekwy.unifabric.proto.common.ResourceSpec;
import com.kekwy.unifabric.proto.provider.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Docker Provider 引擎：使用 docker-java 与 Docker daemon 通信，
 * 实现通用实例容器的部署、停止、移除和状态查询。
 */
public class DockerEngine implements ResourceProvider {

    private static final Logger log = LoggerFactory.getLogger(DockerEngine.class);

    private static final String CONTAINER_ARTIFACT_DIR = "/opt/iarnet/artifact";
    private static final String CONTAINER_FUNCTION_DIR = "/opt/iarnet/function";

    private final DockerClient dockerClient;
    private final ArtifactStore artifactStore;
    private final String network;
    private final String actorRegistryAddr;

    /**
     * instanceId → containerId
     */
    private final Map<String, String> instanceContainers = new ConcurrentHashMap<>();
    /**
     * instanceId → 分配的资源
     */
    private final Map<String, ResourceSpec> instanceResources = new ConcurrentHashMap<>();

    public DockerEngine(String dockerHost, String network,
                        ArtifactStore artifactStore,
                        String actorRegistryAddr) {
        this.network = network != null ? network : "";
        this.artifactStore = artifactStore;
        this.actorRegistryAddr = actorRegistryAddr != null ? actorRegistryAddr : "127.0.0.1:10000";

        DockerClientConfig config = DefaultDockerClientConfig.createDefaultConfigBuilder()
                .withDockerHost(dockerHost != null ? dockerHost : "unix:///var/run/docker.sock")
                .build();

        DockerHttpClient httpClient = new ApacheDockerHttpClient.Builder()
                .dockerHost(config.getDockerHost())
                .sslConfig(config.getSSLConfig())
                .maxConnections(100)
                .connectionTimeout(Duration.ofSeconds(30))
                .responseTimeout(Duration.ofSeconds(45))
                .build();

        this.dockerClient = DockerClientImpl.getInstance(config, httpClient);

        dockerClient.pingCmd().exec();
        log.info("Docker daemon 连接成功: host={}", dockerHost);
    }

    /**
     * 接受预构建的 {@link DockerClient}，用于单元测试。
     */
    /**
     * 接受预构建的 {@link DockerClient}，用于单元测试。
     */
    DockerEngine(DockerClient dockerClient, String network, ArtifactStore artifactStore) {
        this.dockerClient = dockerClient;
        this.network = network != null ? network : "";
        this.artifactStore = artifactStore;
        this.actorRegistryAddr = "127.0.0.1:10000";
    }

    @Override
    public String providerType() {
        return "docker";
    }

    @Override
    public DeployInstanceResponse deployInstance(DeployInstanceRequest request, Path artifactLocalPath) {
        String instanceId = request.getInstanceId();
        String image = request.getImage();
        if (image == null || image.isBlank()) {
            image = "iarnet-actor-java:latest";
        }
        log.info("部署 Docker 实例: instanceId={}, image={}, hasArtifact={}",
                instanceId, image, artifactLocalPath != null);

        try {
            List<String> envList = new ArrayList<>();
            envList.add("IARNET_ACTOR_REGISTRY_ADDR=" + actorRegistryAddr);
            request.getEnvMap().forEach((k, v) -> envList.add(k + "=" + v));

            if (artifactLocalPath != null && java.nio.file.Files.isRegularFile(artifactLocalPath)) {
                String inContainerPath = CONTAINER_ARTIFACT_DIR + "/" + artifactLocalPath.getFileName().toString();
                envList.add("IARNET_ARTIFACT_PATH=" + inContainerPath);
            }

            Map<String, String> labels = new HashMap<>(request.getLabelsMap());
            labels.put("unifabric.managed", "true");
            labels.put("unifabric.instance_id", instanceId);

            var createCmd = dockerClient.createContainerCmd(image)
                    .withName(instanceId)
                    .withEnv(envList)
                    .withLabels(labels)
                    .withHostConfig(buildHostConfig(request, artifactLocalPath));

            CreateContainerResponse container = createCmd.exec();
            String containerId = container.getId();

            if (network != null && !network.isBlank()) {
                dockerClient.connectToNetworkCmd()
                        .withNetworkId(network)
                        .withContainerId(containerId)
                        .exec();
            }

            dockerClient.startContainerCmd(containerId).exec();

            instanceContainers.put(instanceId, containerId);
            instanceResources.put(instanceId, request.getResourceRequest());

            log.info("Docker 实例部署成功: instanceId={}, containerId={}", instanceId, containerId);

            return DeployInstanceResponse.newBuilder()
                    .setInstance(InstanceInfo.newBuilder()
                            .setInstanceId(instanceId)
                            .setStatus(InstanceStatus.RUNNING)
                            .build())
                    .build();

        } catch (Exception e) {
            log.error("Docker 实例部署失败: instanceId={}", instanceId, e);
            return DeployInstanceResponse.newBuilder()
                    .setInstance(InstanceInfo.newBuilder()
                            .setInstanceId(instanceId)
                            .setStatus(InstanceStatus.FAILED)
                            .setMessage(e.getMessage())
                            .build())
                    .build();
        }
    }

    @Override
    public StopInstanceResponse stopInstance(String instanceId) {
        String containerId = instanceContainers.get(instanceId);
        if (containerId == null) {
            return StopInstanceResponse.newBuilder()
                    .setSuccess(false)
                    .setMessage("未找到实例: " + instanceId)
                    .build();
        }

        try {
            dockerClient.stopContainerCmd(containerId).withTimeout(10).exec();
            log.info("Docker 实例已停止: instanceId={}", instanceId);
            return StopInstanceResponse.newBuilder().setSuccess(true).build();
        } catch (Exception e) {
            log.error("停止 Docker 实例失败: instanceId={}", instanceId, e);
            return StopInstanceResponse.newBuilder()
                    .setSuccess(false)
                    .setMessage(e.getMessage())
                    .build();
        }
    }

    @Override
    public RemoveInstanceResponse removeInstance(String instanceId) {
        String containerId = instanceContainers.remove(instanceId);
        if (containerId == null) {
            return RemoveInstanceResponse.newBuilder()
                    .setSuccess(false)
                    .setMessage("未找到实例: " + instanceId)
                    .build();
        }

        try {
            dockerClient.removeContainerCmd(containerId).withForce(true).exec();
            instanceResources.remove(instanceId);
            log.info("Docker 实例已移除: instanceId={}", instanceId);
            return RemoveInstanceResponse.newBuilder().setSuccess(true).build();
        } catch (Exception e) {
            log.error("移除 Docker 实例失败: instanceId={}", instanceId, e);
            instanceContainers.put(instanceId, containerId);
            return RemoveInstanceResponse.newBuilder()
                    .setSuccess(false)
                    .setMessage(e.getMessage())
                    .build();
        }
    }

    @Override
    public GetInstanceStatusResponse getInstanceStatus(String instanceId) {
        String containerId = instanceContainers.get(instanceId);
        if (containerId == null) {
            return GetInstanceStatusResponse.newBuilder()
                    .setInstance(InstanceInfo.newBuilder()
                            .setInstanceId(instanceId)
                            .setStatus(InstanceStatus.INSTANCE_STATUS_UNSPECIFIED)
                            .setMessage("未找到实例")
                            .build())
                    .build();
        }

        try {
            InspectContainerResponse inspect = dockerClient.inspectContainerCmd(containerId).exec();
            InstanceStatus status = mapContainerStatus(inspect.getState());

            return GetInstanceStatusResponse.newBuilder()
                    .setInstance(InstanceInfo.newBuilder()
                            .setInstanceId(instanceId)
                            .setStatus(status)
                            .build())
                    .build();
        } catch (Exception e) {
            return GetInstanceStatusResponse.newBuilder()
                    .setInstance(InstanceInfo.newBuilder()
                            .setInstanceId(instanceId)
                            .setStatus(InstanceStatus.FAILED)
                            .setMessage(e.getMessage())
                            .build())
                    .build();
        }
    }

    @Override
    public void close() throws Exception {
        dockerClient.close();
        log.info("Docker 客户端已关闭");
    }

    // ======================== 内部方法 ========================

    @SuppressWarnings("ConstantValue")
    private HostConfig buildHostConfig(DeployInstanceRequest request, Path artifactLocalPath) {
        ResourceSpec resource = request.getResourceRequest();
        HostConfig hostConfig = HostConfig.newHostConfig();

        if (resource != null && resource.getCpu() > 0) {
            hostConfig.withNanoCPUs((long) (resource.getCpu() * 1_000_000_000));
        }
        if (resource != null && resource.getMemory() != null && !resource.getMemory().isEmpty()) {
            hostConfig.withMemory(parseMemory(resource.getMemory()));
        }
        List<Bind> binds = new ArrayList<>();
        if (artifactLocalPath != null && java.nio.file.Files.isRegularFile(artifactLocalPath)) {
            Path hostDir = artifactLocalPath.getParent();
            binds.add(new Bind(hostDir.toAbsolutePath().toString(), new Volume(CONTAINER_ARTIFACT_DIR)));
        }
        Path functionDir = artifactStore.getActorFunctionDir(request.getInstanceId());
        if (java.nio.file.Files.isDirectory(functionDir)) {
            binds.add(new Bind(functionDir.toAbsolutePath().toString(), new Volume(CONTAINER_FUNCTION_DIR)));
        }
        if (!binds.isEmpty()) {
            hostConfig.withBinds(binds);
        }
        return hostConfig;
    }

    private long parseMemory(String memory) {
        if (memory == null) return 0L;
        memory = memory.trim().toUpperCase();
        if (memory.isEmpty()) return 0L;
        if (memory.endsWith("GI") || memory.endsWith("G")) {
            return (long) (Double.parseDouble(memory.replaceAll("[^\\d.]", "")) * 1024 * 1024 * 1024);
        }
        if (memory.endsWith("MI") || memory.endsWith("M")) {
            return (long) (Double.parseDouble(memory.replaceAll("[^\\d.]", "")) * 1024 * 1024);
        }
        if (memory.endsWith("KI") || memory.endsWith("K")) {
            return (long) (Double.parseDouble(memory.replaceAll("[^\\d.]", "")) * 1024);
        }
        String digits = memory.replaceAll("[^\\d]", "");
        return digits.isEmpty() ? 0L : Long.parseLong(digits);
    }

    private InstanceStatus mapContainerStatus(InspectContainerResponse.ContainerState state) {
        if (state == null) return InstanceStatus.INSTANCE_STATUS_UNSPECIFIED;
        Boolean running = state.getRunning();
        if (Boolean.TRUE.equals(running)) return InstanceStatus.RUNNING;
        Boolean dead = state.getDead();
        if (Boolean.TRUE.equals(dead)) return InstanceStatus.FAILED;
        String status = state.getStatus();
        if (status != null) {
            return switch (status.toLowerCase()) {
                case "created" -> InstanceStatus.PENDING;
                case "running" -> InstanceStatus.RUNNING;
                case "paused", "exited" -> InstanceStatus.STOPPED;
                case "dead" -> InstanceStatus.FAILED;
                case "removing" -> InstanceStatus.REMOVED;
                default -> InstanceStatus.INSTANCE_STATUS_UNSPECIFIED;
            };
        }
        return InstanceStatus.INSTANCE_STATUS_UNSPECIFIED;
    }
}
