package com.kekwy.unifabric.adapter.provider.k8s;

import com.kekwy.unifabric.adapter.artifact.ArtifactStore;
import com.kekwy.unifabric.adapter.provider.InstanceStatusListener;
import com.kekwy.unifabric.adapter.provider.ResourceProvider;
import com.kekwy.unifabric.proto.common.ResourceCapacity;
import com.kekwy.unifabric.proto.common.ResourceSpec;
import com.kekwy.unifabric.proto.provider.*;
import io.fabric8.kubernetes.api.model.*;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import io.fabric8.kubernetes.client.Watch;
import io.fabric8.kubernetes.client.Watcher;
import io.fabric8.kubernetes.client.WatcherException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Kubernetes 资源提供者：将每个实例部署为一个 Pod。
 */
public class KubernetesResourceProvider implements ResourceProvider {

    private static final Logger log = LoggerFactory.getLogger(KubernetesResourceProvider.class);

    private static final String CONTAINER_ARTIFACT_DIR = "/opt/unifabric/artifact";

    private final KubernetesClient kubeClient;
    private final ArtifactStore artifactStore;
    private final String namespace;

    /** instanceId → podName */
    private final Map<String, String> instancePods = new ConcurrentHashMap<>();
    private final Map<String, ResourceSpec> instanceResources = new ConcurrentHashMap<>();
    /** instanceId → 容器内监听端口（论文 3.5.2） */
    private final Map<String, Integer> instanceListenPorts = new ConcurrentHashMap<>();

    private volatile InstanceStatusListener instanceStatusListener;
    private final Map<String, InstanceStatus> lastNotifiedStatus = new ConcurrentHashMap<>();
    private final Object watchLock = new Object();
    private volatile Watch podWatch;

    public KubernetesResourceProvider(String kubeconfig, boolean inCluster, String namespace,
                              ArtifactStore artifactStore) {
        this.namespace = namespace != null ? namespace : "default";
        this.artifactStore = artifactStore;

        if (inCluster) {
            this.kubeClient = new KubernetesClientBuilder().build();
        } else {
            Config config = kubeconfig != null && !kubeconfig.isBlank()
                    ? Config.fromKubeconfig(kubeconfig)
                    : Config.autoConfigure(null);
            this.kubeClient = new KubernetesClientBuilder().withConfig(config).build();
        }

        var version = kubeClient.getKubernetesVersion();
        log.info("K8s 集群连接成功: version={}, namespace={}", version.getGitVersion(), this.namespace);
    }

    KubernetesResourceProvider(KubernetesClient kubeClient, String namespace, ArtifactStore artifactStore) {
        this.kubeClient = kubeClient;
        this.namespace = namespace != null ? namespace : "default";
        this.artifactStore = artifactStore;
    }

    @Override
    public void setInstanceStatusListener(InstanceStatusListener listener) {
        this.instanceStatusListener = listener;
    }

    @Override
    public String providerType() {
        return "k8s";
    }

    @Override
    public DeployInstanceResponse deployInstance(DeployInstanceRequest request, Path artifactLocalPath) {
        String instanceId = request.getInstanceId();
        String podName = sanitizePodName(instanceId);
        log.info("部署 K8s Pod: instanceId={}, podName={}, image={}, hasArtifact={}",
                instanceId, podName, request.getImage(), artifactLocalPath != null);

        try {
            int appPort = defaultAppPort(request);
            instanceListenPorts.put(instanceId, appPort);
            Pod pod = buildPod(podName, request, artifactLocalPath, appPort);

            Pod created = kubeClient.pods().inNamespace(namespace).resource(pod).create();

            instancePods.put(instanceId, created.getMetadata().getName());
            instanceResources.put(instanceId, request.getResourceRequest());

            log.info("K8s Pod 部署成功: instanceId={}, pod={}", instanceId, created.getMetadata().getName());

            ensurePodWatchStarted();

            InstanceInfo.Builder ib = InstanceInfo.newBuilder()
                    .setInstanceId(instanceId)
                    .setStatus(InstanceStatus.RUNNING);
            InstanceEndpoint ep = getInstanceEndpoint(instanceId);
            if (ep != null) {
                ib.setLocalEndpoint(ep);
            }
            return DeployInstanceResponse.newBuilder().setInstance(ib.build()).build();

        } catch (Exception e) {
            log.error("K8s Pod 部署失败: instanceId={}", instanceId, e);
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
        String podName = instancePods.get(instanceId);
        if (podName == null) {
            return StopInstanceResponse.newBuilder()
                    .setSuccess(false)
                    .setMessage("未找到实例: " + instanceId)
                    .build();
        }
        try {
            kubeClient.pods().inNamespace(namespace).withName(podName).delete();
            log.info("K8s Pod 已停止: instanceId={}, pod={}", instanceId, podName);
            return StopInstanceResponse.newBuilder().setSuccess(true).build();
        } catch (Exception e) {
            log.error("停止 K8s Pod 失败: instanceId={}", instanceId, e);
            return StopInstanceResponse.newBuilder()
                    .setSuccess(false)
                    .setMessage(e.getMessage())
                    .build();
        }
    }

    @Override
    public RemoveInstanceResponse removeInstance(String instanceId) {
        String podName = instancePods.remove(instanceId);
        if (podName == null) {
            return RemoveInstanceResponse.newBuilder()
                    .setSuccess(false)
                    .setMessage("未找到实例: " + instanceId)
                    .build();
        }
        try {
            kubeClient.pods().inNamespace(namespace).withName(podName).delete();
            instanceResources.remove(instanceId);
            instanceListenPorts.remove(instanceId);
            lastNotifiedStatus.remove(instanceId);
            log.info("K8s Pod 已移除: instanceId={}, pod={}", instanceId, podName);
            return RemoveInstanceResponse.newBuilder().setSuccess(true).build();
        } catch (Exception e) {
            log.error("移除 K8s Pod 失败: instanceId={}", instanceId, e);
            instancePods.put(instanceId, podName);
            return RemoveInstanceResponse.newBuilder()
                    .setSuccess(false)
                    .setMessage(e.getMessage())
                    .build();
        }
    }

    @Override
    public GetInstanceStatusResponse getInstanceStatus(String instanceId) {
        String podName = instancePods.get(instanceId);
        if (podName == null) {
            return GetInstanceStatusResponse.newBuilder()
                    .setInstance(InstanceInfo.newBuilder()
                            .setInstanceId(instanceId)
                            .setStatus(InstanceStatus.INSTANCE_STATUS_UNSPECIFIED)
                            .setMessage("未找到实例")
                            .build())
                    .build();
        }
        try {
            Pod pod = kubeClient.pods().inNamespace(namespace).withName(podName).get();
            if (pod == null) {
                instancePods.remove(instanceId);
                return GetInstanceStatusResponse.newBuilder()
                        .setInstance(InstanceInfo.newBuilder()
                                .setInstanceId(instanceId)
                                .setStatus(InstanceStatus.REMOVED)
                                .build())
                        .build();
            }
            InstanceStatus status = mapPodPhase(pod.getStatus().getPhase());
            InstanceInfo.Builder ib = InstanceInfo.newBuilder()
                    .setInstanceId(instanceId)
                    .setStatus(status);
            InstanceEndpoint ep = buildEndpointFromPod(pod, instanceId);
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
        String podName = instancePods.get(instanceId);
        if (podName == null) {
            return null;
        }
        try {
            Pod pod = kubeClient.pods().inNamespace(namespace).withName(podName).get();
            return buildEndpointFromPod(pod, instanceId);
        } catch (Exception e) {
            log.debug("解析 K8s 实例端点失败: instanceId={}", instanceId, e);
            return null;
        }
    }

    private InstanceEndpoint buildEndpointFromPod(Pod pod, String instanceId) {
        if (pod == null || pod.getStatus() == null) {
            return null;
        }
        String ip = pod.getStatus().getPodIP();
        if (ip == null || ip.isBlank()) {
            return null;
        }
        int port = instanceListenPorts.getOrDefault(instanceId, 8080);
        return InstanceEndpoint.newBuilder().setHost(ip).setPort(port).build();
    }

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

    @Override
    public ResourceCapacity reportResourceCapacity() {
        try {
            var nodeList = kubeClient.nodes().list().getItems();
            if (nodeList == null || nodeList.isEmpty()) {
                return ResourceCapacity.getDefaultInstance();
            }
            var alloc = nodeList.get(0).getStatus().getAllocatable();
            double totalCpu = parseCpu(alloc != null ? alloc.get("cpu") : null);
            long totalMem = parseMemBytes(alloc != null ? alloc.get("memory") : null);

            double usedCpu = 0;
            long usedMem = 0;
            for (ResourceSpec r : instanceResources.values()) {
                if (r == null) continue;
                usedCpu += r.getCpu();
                usedMem += parseMemString(r.getMemory());
            }

            double availCpu = Math.max(0, totalCpu - usedCpu);
            long availMem = Math.max(0, totalMem - usedMem);

            ResourceSpec total = ResourceSpec.newBuilder()
                    .setCpu(totalCpu)
                    .setMemory(Long.toString(totalMem))
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
            log.warn("采集 K8s 资源容量失败", e);
            return ResourceCapacity.getDefaultInstance();
        }
    }

    @Override
    public void close() {
        synchronized (watchLock) {
            if (podWatch != null) {
                try {
                    podWatch.close();
                } catch (Exception e) {
                    log.debug("关闭 Pod Watch: {}", e.getMessage());
                }
                podWatch = null;
            }
        }
        kubeClient.close();
        log.info("K8s 客户端已关闭");
    }

    private void ensurePodWatchStarted() {
        synchronized (watchLock) {
            if (podWatch != null) {
                return;
            }
            podWatch = kubeClient.pods().inNamespace(namespace)
                    .withLabel("unifabric.managed", "true")
                    .watch(new Watcher<Pod>() {
                        @Override
                        public void eventReceived(Action action, Pod pod) {
                            onPodWatchEvent(action, pod);
                        }

                        @Override
                        public void onClose(WatcherException cause) {
                            synchronized (watchLock) {
                                KubernetesResourceProvider.this.podWatch = null;
                            }
                            if (cause != null) {
                                log.debug("Pod Watch 关闭: {}", cause.getMessage());
                            }
                        }
                    });
            log.info("K8s Pod Watch 已启动（label unifabric.managed=true）");
        }
    }

    private void onPodWatchEvent(Watcher.Action action, Pod pod) {
        if (pod == null || pod.getMetadata() == null || pod.getMetadata().getLabels() == null) {
            return;
        }
        String instanceId = pod.getMetadata().getLabels().get("unifabric.instance_id");
        if (instanceId == null || instanceId.isEmpty() || !instancePods.containsKey(instanceId)) {
            return;
        }
        InstanceStatus next;
        if (action == Watcher.Action.DELETED) {
            next = InstanceStatus.REMOVED;
        } else {
            String phase = pod.getStatus() != null ? pod.getStatus().getPhase() : null;
            next = mapPodPhase(phase);
        }
        if (next == InstanceStatus.INSTANCE_STATUS_UNSPECIFIED) {
            return;
        }
        emitInstanceStatus(instanceId, next, action != null ? action.name() : "");
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

    private static double parseCpu(Quantity q) {
        if (q == null) return 0;
        String a = q.getAmount();
        if (a == null || a.isEmpty()) return 0;
        try {
            if (a.endsWith("m")) {
                return Double.parseDouble(a.substring(0, a.length() - 1)) / 1000.0;
            }
            return Double.parseDouble(a);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static long parseMemBytes(Quantity q) {
        if (q == null) return 0;
        String a = q.getAmount();
        if (a == null || a.isEmpty()) return 0;
        try {
            return parseK8sQuantityBytes(a);
        } catch (Exception e) {
            return 0;
        }
    }

    private static long parseK8sQuantityBytes(String a) {
        int i = 0;
        while (i < a.length() && (Character.isDigit(a.charAt(i)) || a.charAt(i) == '.')) {
            i++;
        }
        if (i == 0) return 0;
        double num = Double.parseDouble(a.substring(0, i));
        String suffix = a.substring(i).trim();
        return switch (suffix) {
            case "Ki" -> (long) (num * 1024);
            case "Mi" -> (long) (num * 1024 * 1024);
            case "Gi" -> (long) (num * 1024 * 1024 * 1024);
            case "Ti" -> (long) (num * 1024L * 1024 * 1024 * 1024);
            case "K", "k" -> (long) (num * 1000);
            case "M" -> (long) (num * 1000 * 1000);
            case "G" -> (long) (num * 1000 * 1000 * 1000);
            default -> (long) num;
        };
    }

    private static long parseMemString(String memory) {
        if (memory == null || memory.isBlank()) return 0;
        memory = memory.trim().toUpperCase();
        try {
            if (memory.endsWith("GI") || memory.endsWith("G")) {
                return (long) (Double.parseDouble(memory.replaceAll("[^\\d.]", "")) * 1024 * 1024 * 1024);
            }
            if (memory.endsWith("MI") || memory.endsWith("M")) {
                return (long) (Double.parseDouble(memory.replaceAll("[^\\d.]", "")) * 1024 * 1024);
            }
            return Long.parseLong(memory.replaceAll("[^\\d]", ""));
        } catch (Exception e) {
            return 0;
        }
    }

    private Pod buildPod(String podName, DeployInstanceRequest request, Path artifactLocalPath, int containerPort) {
        ResourceSpec resource = request.getResourceRequest();
        Map<String, Quantity> requests = new HashMap<>();
        if (resource != null && resource.getCpu() > 0) {
            requests.put("cpu", new Quantity(Double.toString(resource.getCpu())));
        }
        if (resource != null && resource.getMemory() != null && !resource.getMemory().isBlank()) {
            requests.put("memory", new Quantity(resource.getMemory()));
        }
        List<EnvVar> envVars = new ArrayList<>();
        request.getEnvMap().forEach((k, v) -> envVars.add(new EnvVar(k, v, null)));
        if (artifactLocalPath != null && java.nio.file.Files.isRegularFile(artifactLocalPath)) {
            envVars.add(new EnvVar("UNIFABRIC_ARTIFACT_PATH", CONTAINER_ARTIFACT_DIR + "/" + artifactLocalPath.getFileName(), null));
        }

        String image = request.getImage();
        if (image == null || image.isBlank()) {
            image = "alpine:latest";
        }

        ContainerBuilder cb = new ContainerBuilder()
                .withName("instance")
                .withImage(image)
                .withEnv(envVars)
                .addNewPort()
                    .withContainerPort(containerPort)
                    .withProtocol("TCP")
                .endPort()
                .withResources(new ResourceRequirementsBuilder().withRequests(requests).build());
        if (artifactLocalPath != null && java.nio.file.Files.isRegularFile(artifactLocalPath)) {
            cb.addNewVolumeMount().withName("artifact").withMountPath(CONTAINER_ARTIFACT_DIR).endVolumeMount();
        }

        PodBuilder pb = new PodBuilder()
                .withNewMetadata()
                    .withName(podName)
                    .withNamespace(namespace)
                    .addToLabels("unifabric.managed", "true")
                    .addToLabels("unifabric.instance_id", request.getInstanceId())
                .endMetadata()
                .withNewSpec()
                    .withContainers(cb.build())
                    .withRestartPolicy("Never")
                .endSpec();

        if (artifactLocalPath != null && java.nio.file.Files.isRegularFile(artifactLocalPath)) {
            pb.editSpec()
                    .addNewVolume()
                        .withName("artifact")
                        .withNewHostPath().withPath(artifactLocalPath.getParent().toAbsolutePath().toString()).endHostPath()
                    .endVolume()
                .endSpec();
        }
        return pb.build();
    }

    private String sanitizePodName(String name) {
        String s = name.toLowerCase().replaceAll("[^a-z0-9\\-.]", "-");
        while (s.startsWith("-") || s.startsWith(".")) s = s.substring(1);
        while (s.endsWith("-") || s.endsWith(".")) s = s.substring(0, s.length() - 1);
        if (s.length() > 253) s = s.substring(0, 253);
        return s.isEmpty() ? "pod" : s;
    }

    private InstanceStatus mapPodPhase(String phase) {
        if (phase == null) return InstanceStatus.INSTANCE_STATUS_UNSPECIFIED;
        return switch (phase) {
            case "Pending" -> InstanceStatus.PENDING;
            case "Running" -> InstanceStatus.RUNNING;
            case "Succeeded" -> InstanceStatus.STOPPED;
            case "Failed" -> InstanceStatus.FAILED;
            default -> InstanceStatus.INSTANCE_STATUS_UNSPECIFIED;
        };
    }
}
