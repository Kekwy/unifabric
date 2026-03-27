package com.kekwy.unifabric.adapter.provider.docker;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.api.model.Event;
import com.github.dockerjava.api.model.EventType;
import com.github.dockerjava.api.model.*;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient;
import com.github.dockerjava.transport.DockerHttpClient;
import com.kekwy.unifabric.adapter.artifact.ArtifactStore;
import com.kekwy.unifabric.adapter.provider.InstanceStatusListener;
import com.kekwy.unifabric.adapter.provider.ResourceProvider;
import com.kekwy.unifabric.proto.common.ResourceCapacity;
import com.kekwy.unifabric.proto.common.ResourceSpec;
import com.kekwy.unifabric.proto.provider.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Docker 资源提供者：使用 docker-java 与 Docker daemon 通信，
 * 实现通用实例容器的部署、停止、移除和状态查询。
 */
public class DockerResourceProvider implements ResourceProvider {

    private static final Logger log = LoggerFactory.getLogger(DockerResourceProvider.class);

    private static final String CONTAINER_ARTIFACT_DIR = "/opt/unifabric/artifact";

    private final DockerClient dockerClient;
    private final ArtifactStore artifactStore;
    private final String network;

    /**
     * instanceId → containerId
     */
    private final Map<String, String> instanceContainers = new ConcurrentHashMap<>();
    /**
     * instanceId → 分配的资源
     */
    private final Map<String, ResourceSpec> instanceResources = new ConcurrentHashMap<>();

    private volatile InstanceStatusListener instanceStatusListener;
    private final Map<String, InstanceStatus> lastNotifiedStatus = new ConcurrentHashMap<>();
    private final Object eventsLock = new Object();
    private ResultCallback<Event> dockerEventsCallback;

    public DockerResourceProvider(String dockerHost, String network,
                          ArtifactStore artifactStore) {
        this.network = network != null ? network : "";
        this.artifactStore = artifactStore;

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
    DockerResourceProvider(DockerClient dockerClient, String network, ArtifactStore artifactStore) {
        this.dockerClient = dockerClient;
        this.network = network != null ? network : "";
        this.artifactStore = artifactStore;
    }

    @Override
    public void setInstanceStatusListener(InstanceStatusListener listener) {
        this.instanceStatusListener = listener;
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
            image = "alpine:latest";
        }
        log.info("部署 Docker 实例: instanceId={}, image={}, hasArtifact={}",
                instanceId, image, artifactLocalPath != null);

        try {
            List<String> envList = new ArrayList<>();
            request.getEnvMap().forEach((k, v) -> envList.add(k + "=" + v));

            if (artifactLocalPath != null && java.nio.file.Files.isRegularFile(artifactLocalPath)) {
                String inContainerPath = CONTAINER_ARTIFACT_DIR + "/" + artifactLocalPath.getFileName().toString();
                envList.add("UNIFABRIC_ARTIFACT_PATH=" + inContainerPath);
            }

            Map<String, String> labels = new HashMap<>(request.getLabelsMap());
            labels.put("unifabric.managed", "true");
            labels.put("unifabric.instance_id", instanceId);

            var createCmd = dockerClient.createContainerCmd(image)
                    .withName(instanceId)
                    .withEnv(envList)
                    .withLabels(labels)
                    .withExposedPorts(new ExposedPort(defaultAppPort(request)))
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

            ensureDockerEventsStarted();

            InstanceInfo.Builder ib = InstanceInfo.newBuilder()
                    .setInstanceId(instanceId)
                    .setStatus(InstanceStatus.RUNNING);
            InstanceEndpoint ep = getInstanceEndpoint(instanceId);
            if (ep != null) {
                ib.setLocalEndpoint(ep);
            }
            return DeployInstanceResponse.newBuilder().setInstance(ib.build()).build();

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
            lastNotifiedStatus.remove(instanceId);
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
            InstanceInfo.Builder ib = InstanceInfo.newBuilder()
                    .setInstanceId(instanceId)
                    .setStatus(status);
            InstanceEndpoint ep = buildEndpointFromInspect(inspect);
            if (ep != null) {
                ib.setLocalEndpoint(ep);
            }
            return GetInstanceStatusResponse.newBuilder().setInstance(ib.build()).build();
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
    public InstanceEndpoint getInstanceEndpoint(String instanceId) {
        String containerId = instanceContainers.get(instanceId);
        if (containerId == null) {
            return null;
        }
        try {
            InspectContainerResponse inspect = dockerClient.inspectContainerCmd(containerId).exec();
            return buildEndpointFromInspect(inspect);
        } catch (Exception e) {
            log.debug("解析 Docker 实例端点失败: instanceId={}", instanceId, e);
            return null;
        }
    }

    /**
     * 应用监听端口：优先环境变量 {@code UNIFABRIC_APP_PORT}，默认 8080（论文 3.5.2）。
     */
    private static int defaultAppPort(DeployInstanceRequest request) {
        if (request == null) {
            return 8080;
        }
        String p = request.getEnvMap().get("UNIFABRIC_APP_PORT");
        if (p != null && !p.isBlank()) {
            try {
                return Integer.parseInt(p.trim());
            } catch (NumberFormatException ignored) {
                // fall through
            }
        }
        return 8080;
    }

    private InstanceEndpoint buildEndpointFromInspect(InspectContainerResponse inspect) {
        if (inspect == null) {
            return null;
        }
        String ip = null;
        var ns = inspect.getNetworkSettings();
        if (ns != null && ns.getNetworks() != null) {
            for (ContainerNetwork cn : ns.getNetworks().values()) {
                if (cn != null && cn.getIpAddress() != null && !cn.getIpAddress().isBlank()) {
                    ip = cn.getIpAddress();
                    break;
                }
            }
        }
        int port = 8080;
        if (inspect.getConfig() != null && inspect.getConfig().getExposedPorts() != null) {
            ExposedPort[] exposed = inspect.getConfig().getExposedPorts();
            if (exposed != null && exposed.length > 0) {
                port = exposed[0].getPort();
            }
        }
        if (ip == null || ip.isBlank()) {
            return null;
        }
        return InstanceEndpoint.newBuilder().setHost(ip).setPort(port).build();
    }

    @Override
    public ResourceCapacity reportResourceCapacity() {
        try {
            com.github.dockerjava.api.model.Info info = dockerClient.infoCmd().exec();
            int ncpu = info.getNCPU() != null ? info.getNCPU() : 0;
            long memTotal = info.getMemTotal() != null ? info.getMemTotal() : 0L;

            double usedCpu = 0;
            long usedMem = 0;
            for (ResourceSpec r : instanceResources.values()) {
                if (r == null) continue;
                usedCpu += r.getCpu();
                usedMem += parseMemory(r.getMemory());
            }

            double availCpu = Math.max(0, ncpu - usedCpu);
            long availMem = Math.max(0, memTotal - usedMem);

            ResourceSpec total = ResourceSpec.newBuilder()
                    .setCpu(ncpu)
                    .setMemory(Long.toString(memTotal))
                    .setGpu(0)
                    .build();
            ResourceSpec used = ResourceSpec.newBuilder()
                    .setCpu(usedCpu)
                    .setMemory(Long.toString(usedMem))
                    .setGpu(0)
                    .build();
            ResourceSpec available = ResourceSpec.newBuilder()
                    .setCpu(availCpu)
                    .setMemory(Long.toString(availMem))
                    .setGpu(0)
                    .build();
            return ResourceCapacity.newBuilder()
                    .setTotal(total)
                    .setUsed(used)
                    .setAvailable(available)
                    .build();
        } catch (Exception e) {
            log.warn("采集 Docker 资源容量失败", e);
            return ResourceCapacity.getDefaultInstance();
        }
    }

    @Override
    public void close() throws Exception {
        synchronized (eventsLock) {
            if (dockerEventsCallback != null) {
                try {
                    dockerEventsCallback.close();
                } catch (Exception e) {
                    log.debug("关闭 Docker events 流: {}", e.getMessage());
                }
                dockerEventsCallback = null;
            }
        }
        dockerClient.close();
        log.info("Docker 客户端已关闭");
    }

    private void ensureDockerEventsStarted() {
        synchronized (eventsLock) {
            if (dockerEventsCallback != null) {
                return;
            }
            ResultCallback.Adapter<Event> cb = new ResultCallback.Adapter<>() {
                @Override
                public void onNext(Event event) {
                    handleDockerEvent(event);
                }
            };
            dockerClient.eventsCmd()
                    .withEventTypeFilter("container")
                    .withLabelFilter("unifabric.managed=true")
                    .exec(cb);
            dockerEventsCallback = cb;
            log.info("Docker 容器事件监听已启动（unifabric.managed）");
        }
    }

    private void handleDockerEvent(Event event) {
        if (event == null || event.getType() != EventType.CONTAINER) {
            return;
        }
        String cid = event.getActor() != null ? event.getActor().getId() : null;
        if (cid == null || cid.isEmpty()) {
            return;
        }
        String instanceId = null;
        for (Map.Entry<String, String> e : instanceContainers.entrySet()) {
            if (cid.equals(e.getValue())) {
                instanceId = e.getKey();
                break;
            }
        }
        if (instanceId == null) {
            return;
        }
        String action = event.getStatus();
        if (action == null) {
            return;
        }
        InstanceStatus next = mapDockerLifecycleAction(action, event);
        if (next == null || next == InstanceStatus.INSTANCE_STATUS_UNSPECIFIED) {
            return;
        }
        emitInstanceStatus(instanceId, next, action);
    }

    private static InstanceStatus mapDockerLifecycleAction(String action, Event event) {
        String a = action.toLowerCase(Locale.ROOT);
        return switch (a) {
            case "start" -> InstanceStatus.RUNNING;
            case "stop" -> InstanceStatus.STOPPED;
            case "kill" -> InstanceStatus.FAILED;
            case "destroy" -> InstanceStatus.REMOVED;
            case "die" -> {
                String exit = "";
                if (event.getActor() != null && event.getActor().getAttributes() != null) {
                    String c = event.getActor().getAttributes().get("exitCode");
                    exit = c != null ? c : "";
                }
                yield (!exit.isEmpty() && !"0".equals(exit)) ? InstanceStatus.FAILED : InstanceStatus.STOPPED;
            }
            default -> null;
        };
    }

    private void emitInstanceStatus(String instanceId, InstanceStatus next, String detail) {
        InstanceStatus prev = lastNotifiedStatus.get(instanceId);
        if (prev == next) {
            return;
        }
        lastNotifiedStatus.put(instanceId, next);
        InstanceStatusListener l = instanceStatusListener;
        if (l != null) {
            l.onStatusChanged(instanceId,
                    prev != null ? prev : InstanceStatus.INSTANCE_STATUS_UNSPECIFIED,
                    next,
                    detail);
        }
    }

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
