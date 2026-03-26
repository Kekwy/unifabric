package com.kekwy.unifabric.adapter.engine;

import com.kekwy.unifabric.proto.provider.DeployActorRequest;
import com.kekwy.unifabric.proto.provider.DeployActorResponse;
import com.kekwy.unifabric.proto.provider.GetActorStatusResponse;
import com.kekwy.unifabric.proto.provider.RemoveActorResponse;
import com.kekwy.unifabric.proto.provider.StopActorResponse;

import java.nio.file.Path;
import java.util.Map;

/**
 * 资源引擎 SPI：每种运行时（Docker / K8s）提供一个实现，
 * 负责计算实例的部署、停止、移除与状态查询。
 */
public interface ResourceEngine extends AutoCloseable {

    /** 引擎类型标识，如 "docker"、"k8s" */
    String providerType();

    /**
     * 部署一个 Actor。
     *
     * @param request               部署请求（含 actor_id、artifact_url、resource_request、lang、function_descriptor 等）
     * @param artifactLocalPath     已拉取到本地的 artifact 文件路径；为 null 表示无 artifact
     * @param conditionFunctionPaths output_port -> 条件函数文件路径；可为 null 或空表示无条件分支
     */
    DeployActorResponse deployActor(DeployActorRequest request, Path artifactLocalPath,
                                    Map<Integer, Path> conditionFunctionPaths);

    /** 停止一个 Actor */
    StopActorResponse stopActor(String actorId);

    /** 移除一个 Actor */
    RemoveActorResponse removeActor(String actorId);

    /** 查询 Actor 状态 */
    GetActorStatusResponse getActorStatus(String actorId);
}
