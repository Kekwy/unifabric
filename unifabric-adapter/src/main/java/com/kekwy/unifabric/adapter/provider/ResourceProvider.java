package com.kekwy.unifabric.adapter.provider;

import com.kekwy.unifabric.proto.common.ResourceCapacity;
import com.kekwy.unifabric.proto.provider.DeployInstanceRequest;
import com.kekwy.unifabric.proto.provider.DeployInstanceResponse;
import com.kekwy.unifabric.proto.provider.GetInstanceStatusResponse;
import com.kekwy.unifabric.proto.provider.InstanceEndpoint;
import com.kekwy.unifabric.proto.provider.RemoveInstanceResponse;
import com.kekwy.unifabric.proto.provider.StopInstanceResponse;

import java.nio.file.Path;

/**
 * 资源提供者 SPI：每种运行时提供一个实现（如 {@link com.kekwy.unifabric.adapter.provider.docker.DockerResourceProvider}、
 * {@link com.kekwy.unifabric.adapter.provider.k8s.KubernetesResourceProvider}），
 * 负责通用计算实例的部署、停止、移除与状态查询。
 */
public interface ResourceProvider extends AutoCloseable {

    /** 提供者类型标识，如 "docker"、"k8s" */
    String providerType();

    /**
     * 部署一个通用实例。
     *
     * @param request           通用部署请求（instance_id、image、resource_request、env、labels）
     * @param artifactLocalPath 已拉取到本地的 artifact 文件路径；为 null 表示无 artifact
     */
    DeployInstanceResponse deployInstance(DeployInstanceRequest request, Path artifactLocalPath);

    /** 停止一个实例 */
    StopInstanceResponse stopInstance(String instanceId);

    /** 移除一个实例 */
    RemoveInstanceResponse removeInstance(String instanceId);

    /** 查询实例状态 */
    GetInstanceStatusResponse getInstanceStatus(String instanceId);

    /**
     * 解析实例在 Provider 本地网络中的可达端点（论文 3.5.2）；未知时返回 {@code null}。
     */
    default InstanceEndpoint getInstanceEndpoint(String instanceId) {
        return null;
    }

    /** 采集当前节点资源总量 / 已用量 / 可用量，供控制通道上报 */
    ResourceCapacity reportResourceCapacity();

    /** 设置实例状态监听；用于 Docker/K8s 事件驱动上报（论文 3.2.4） */
    default void setInstanceStatusListener(InstanceStatusListener listener) {
    }
}
