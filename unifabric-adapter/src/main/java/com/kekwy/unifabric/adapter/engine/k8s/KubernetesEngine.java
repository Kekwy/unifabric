package com.kekwy.unifabric.adapter.engine.k8s;

import com.kekwy.unifabric.adapter.artifact.ArtifactStore;
import com.kekwy.unifabric.adapter.engine.ResourceProvider;
import com.kekwy.unifabric.proto.common.ResourceSpec;
import com.kekwy.unifabric.proto.provider.*;
import io.fabric8.kubernetes.api.model.*;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Kubernetes Provider 引擎：将每个实例部署为一个 Pod。
 */
public class KubernetesEngine implements ResourceProvider {

    private static final Logger log = LoggerFactory.getLogger(KubernetesEngine.class);

    private static final String CONTAINER_FUNCTION_DIR = "/opt/iarnet/function";
    private static final String CONTAINER_ARTIFACT_DIR = "/opt/iarnet/artifact";

    private final KubernetesClient kubeClient;
    private final ArtifactStore artifactStore;
    private final String namespace;

    /** instanceId → podName */
    private final Map<String, String> instancePods = new ConcurrentHashMap<>();
    private final Map<String, ResourceSpec> instanceResources = new ConcurrentHashMap<>();

    public KubernetesEngine(String kubeconfig, boolean inCluster, String namespace,
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

    KubernetesEngine(KubernetesClient kubeClient, String namespace, ArtifactStore artifactStore) {
        this.kubeClient = kubeClient;
        this.namespace = namespace != null ? namespace : "default";
        this.artifactStore = artifactStore;
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
            Pod pod = buildPod(podName, request, artifactLocalPath);

            Pod created = kubeClient.pods().inNamespace(namespace).resource(pod).create();

            instancePods.put(instanceId, created.getMetadata().getName());
            instanceResources.put(instanceId, request.getResourceRequest());

            log.info("K8s Pod 部署成功: instanceId={}, pod={}", instanceId, created.getMetadata().getName());

            return DeployInstanceResponse.newBuilder()
                    .setInstance(InstanceInfo.newBuilder()
                            .setInstanceId(instanceId)
                            .setStatus(InstanceStatus.RUNNING)
                            .build())
                    .build();

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
    public void close() {
        kubeClient.close();
        log.info("K8s 客户端已关闭");
    }

    private Pod buildPod(String podName, DeployInstanceRequest request, Path artifactLocalPath) {
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
            envVars.add(new EnvVar("IARNET_ARTIFACT_PATH", CONTAINER_ARTIFACT_DIR + "/" + artifactLocalPath.getFileName(), null));
        }

        ContainerBuilder cb = new ContainerBuilder()
                .withName("instance")
                .withImage(request.getImage())
                .withEnv(envVars)
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
