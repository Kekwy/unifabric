package com.kekwy.unifabric.adapter.engine;

import com.kekwy.unifabric.proto.provider.DeployInstanceRequest;
import com.kekwy.unifabric.proto.provider.DeployInstanceResponse;
import com.kekwy.unifabric.proto.provider.GetInstanceStatusResponse;
import com.kekwy.unifabric.proto.provider.RemoveInstanceResponse;
import com.kekwy.unifabric.proto.provider.StopInstanceResponse;

import java.nio.file.Path;

/**
 * 资源提供者 SPI：每种运行时（Docker / K8s）提供一个实现，
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
}
