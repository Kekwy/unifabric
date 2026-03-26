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
import com.kekwy.unifabric.adapter.engine.ResourceEngine;
import com.kekwy.unifabric.proto.common.FunctionDescriptor;
import com.kekwy.unifabric.proto.common.Lang;
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
 * 实现 Actor 容器的部署、停止、移除和状态查询。
 */
public class DockerEngine implements ResourceEngine {

    private static final Logger log = LoggerFactory.getLogger(DockerEngine.class);

    private static final String CONTAINER_ARTIFACT_DIR = "/opt/iarnet/artifact";
    private static final String CONTAINER_FUNCTION_DIR = "/opt/iarnet/function";
    private static final String CONTAINER_FUNCTION_FILE = CONTAINER_FUNCTION_DIR + "/function.pb";
    private static final String CONTAINER_CONDITIONS_DIR = CONTAINER_FUNCTION_DIR + "/conditions";

    private final DockerClient dockerClient;
    private final ArtifactStore artifactStore;
    private final String network;
    private final String actorRegistryAddr;

    /**
     * actorId → containerId
     */
    private final Map<String, String> actorContainers = new ConcurrentHashMap<>();
    /**
     * actorId → 分配的资源
     */
    private final Map<String, ResourceSpec> actorResources = new ConcurrentHashMap<>();

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

    private String resolveImageForLang(Lang lang) {
        if (lang == null) {
            return "iarnet-actor-java:latest";
        }
        return switch (lang) {
            case LANG_PYTHON -> "iarnet-actor-python:latest";
            default -> "iarnet-actor-java:latest";
        };
    }

    @Override
    public DeployActorResponse deployActor(DeployActorRequest request, Path artifactLocalPath,
                                           Map<Integer, Path> conditionFunctionPaths) {
        String actorId = request.getActorId();
        Lang lang = request.getLang();
        String image = resolveImageForLang(lang);
        boolean hasConditions = conditionFunctionPaths != null && !conditionFunctionPaths.isEmpty();
        log.info("部署 Docker Actor: actorId={}, lang={}, image={}, hasArtifact={}, hasFunctionDescriptor={}, hasConditions={}",
                actorId, lang, image, artifactLocalPath != null, request.hasFunctionDescriptor(), hasConditions);

        try {
            Path functionDescriptorPath = null;
            if (request.hasFunctionDescriptor()) {
                FunctionDescriptor fd = request.getFunctionDescriptor();
                functionDescriptorPath = artifactStore.storeFunctionDescriptor(actorId, fd.toByteArray());
            }

            List<String> envList = new ArrayList<>();
            envList.add("IARNET_ACTOR_ID=" + actorId);
            envList.add("IARNET_ACTOR_REGISTRY_ADDR=" + actorRegistryAddr);

            if (artifactLocalPath != null && java.nio.file.Files.isRegularFile(artifactLocalPath)) {
                String inContainerPath = CONTAINER_ARTIFACT_DIR + "/" + artifactLocalPath.getFileName().toString();
                envList.add("IARNET_ARTIFACT_PATH=" + inContainerPath);
            }
            if (functionDescriptorPath != null) {
                envList.add("IARNET_ACTOR_FUNCTION_FILE=" + CONTAINER_FUNCTION_FILE);
            }
            if (hasConditions) {
                envList.add("IARNET_CONDITION_FUNCTIONS_DIR=" + CONTAINER_CONDITIONS_DIR);
            }
            envList.add("IARNET_NODE_KIND=" + request.getNodeKind().name());

            Map<String, String> labels = new HashMap<>();
            labels.put("unifabric.managed", "true");
            labels.put("unifabric.actor_id", actorId);

            var createCmd = dockerClient.createContainerCmd(image)
                    .withName(actorId)
                    .withEnv(envList)
                    .withLabels(labels)
                    .withHostConfig(
                            buildHostConfig(
                                    request,
                                    artifactLocalPath,
                                    functionDescriptorPath,
                                    hasConditions
                            )
                    );

            CreateContainerResponse container = createCmd.exec();
            String containerId = container.getId();

            if (network != null && !network.isBlank()) {
                dockerClient.connectToNetworkCmd()
                        .withNetworkId(network)
                        .withContainerId(containerId)
                        .exec();
            }

            dockerClient.startContainerCmd(containerId).exec();

            actorContainers.put(actorId, containerId);
            actorResources.put(actorId, request.getResourceRequest());

            log.info("Docker Actor 部署成功: actorId={}, containerId={}", actorId, containerId);

            return DeployActorResponse.newBuilder()
                    .setActor(ActorInfo.newBuilder()
                            .setActorId(actorId)
                            .setStatus(ActorStatus.RUNNING)
                            .build())
                    .build();

        } catch (Exception e) {
            log.error("Docker Actor 部署失败: actorId={}", actorId, e);
            return DeployActorResponse.newBuilder()
                    .setActor(ActorInfo.newBuilder()
                            .setActorId(actorId)
                            .setStatus(ActorStatus.FAILED)
                            .setMessage(e.getMessage())
                            .build())
                    .build();
        }
    }

    @Override
    public StopActorResponse stopActor(String actorId) {
        String containerId = actorContainers.get(actorId);
        if (containerId == null) {
            return StopActorResponse.newBuilder()
                    .setSuccess(false)
                    .setMessage("未找到 Actor: " + actorId)
                    .build();
        }

        try {
            dockerClient.stopContainerCmd(containerId).withTimeout(10).exec();
            log.info("Docker Actor 已停止: actorId={}", actorId);
            return StopActorResponse.newBuilder().setSuccess(true).build();
        } catch (Exception e) {
            log.error("停止 Docker Actor 失败: actorId={}", actorId, e);
            return StopActorResponse.newBuilder()
                    .setSuccess(false)
                    .setMessage(e.getMessage())
                    .build();
        }
    }

    @Override
    public RemoveActorResponse removeActor(String actorId) {
        String containerId = actorContainers.remove(actorId);
        if (containerId == null) {
            return RemoveActorResponse.newBuilder()
                    .setSuccess(false)
                    .setMessage("未找到 Actor: " + actorId)
                    .build();
        }

        try {
            dockerClient.removeContainerCmd(containerId).withForce(true).exec();
            actorResources.remove(actorId);
            log.info("Docker Actor 已移除: actorId={}", actorId);
            return RemoveActorResponse.newBuilder().setSuccess(true).build();
        } catch (Exception e) {
            log.error("移除 Docker Actor 失败: actorId={}", actorId, e);
            actorContainers.put(actorId, containerId);
            return RemoveActorResponse.newBuilder()
                    .setSuccess(false)
                    .setMessage(e.getMessage())
                    .build();
        }
    }

    @Override
    public GetActorStatusResponse getActorStatus(String actorId) {
        String containerId = actorContainers.get(actorId);
        if (containerId == null) {
            return GetActorStatusResponse.newBuilder()
                    .setActor(ActorInfo.newBuilder()
                            .setActorId(actorId)
                            .setStatus(ActorStatus.ACTOR_STATUS_UNSPECIFIED)
                            .setMessage("未找到 Actor")
                            .build())
                    .build();
        }

        try {
            InspectContainerResponse inspect = dockerClient.inspectContainerCmd(containerId).exec();
            ActorStatus status = mapContainerStatus(inspect.getState());

            return GetActorStatusResponse.newBuilder()
                    .setActor(ActorInfo.newBuilder()
                            .setActorId(actorId)
                            .setStatus(status)
                            .build())
                    .build();
        } catch (Exception e) {
            return GetActorStatusResponse.newBuilder()
                    .setActor(ActorInfo.newBuilder()
                            .setActorId(actorId)
                            .setStatus(ActorStatus.FAILED)
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
    private HostConfig buildHostConfig(DeployActorRequest request, Path artifactLocalPath,
                                       Path functionDescriptorPath, boolean hasConditions) {
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
        if (functionDescriptorPath != null && java.nio.file.Files.isRegularFile(functionDescriptorPath)) {
            String hostDir = functionDescriptorPath.getParent().toAbsolutePath().toString();
            binds.add(new Bind(hostDir, new Volume(CONTAINER_FUNCTION_DIR)));
        }
        if (hasConditions && functionDescriptorPath == null) {
            Path actorFunctionDir = artifactStore.getActorFunctionDir(request.getActorId());
            if (java.nio.file.Files.isDirectory(actorFunctionDir)) {
                binds.add(new Bind(actorFunctionDir.toAbsolutePath().toString(), new Volume(CONTAINER_FUNCTION_DIR)));
            }
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

    private ActorStatus mapContainerStatus(InspectContainerResponse.ContainerState state) {
        if (state == null) return ActorStatus.ACTOR_STATUS_UNSPECIFIED;
        Boolean running = state.getRunning();
        if (Boolean.TRUE.equals(running)) return ActorStatus.RUNNING;
        Boolean dead = state.getDead();
        if (Boolean.TRUE.equals(dead)) return ActorStatus.FAILED;
        String status = state.getStatus();
        if (status != null) {
            return switch (status.toLowerCase()) {
                case "created" -> ActorStatus.PENDING;
                case "running" -> ActorStatus.RUNNING;
                case "paused", "exited" -> ActorStatus.STOPPED;
                case "dead" -> ActorStatus.FAILED;
                case "removing" -> ActorStatus.REMOVED;
                default -> ActorStatus.ACTOR_STATUS_UNSPECIFIED;
            };
        }
        return ActorStatus.ACTOR_STATUS_UNSPECIFIED;
    }
}
