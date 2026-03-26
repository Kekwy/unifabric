package com.kekwy.unifabric.actor;

import java.nio.file.Path;

/**
 * Actor 运行时配置，从环境变量解析。
 */
public final class ActorConfig {

    private static final String ENV_ACTOR_ID = "IARNET_ACTOR_ID";
    private static final String ENV_ACTOR_REGISTRY_ADDR = "IARNET_ACTOR_REGISTRY_ADDR";
    private static final String ENV_ACTOR_FUNCTION_FILE = "IARNET_ACTOR_FUNCTION_FILE";
    private static final String ENV_ARTIFACT_PATH = "IARNET_ARTIFACT_PATH";
    private static final String ENV_CONDITION_FUNCTIONS_DIR = "IARNET_CONDITION_FUNCTIONS_DIR";
    private static final String ENV_NODE_KIND = "IARNET_NODE_KIND";
    private static final String ENV_COMBINE_TIMEOUT_MS = "IARNET_COMBINE_TIMEOUT_MS";

    private static final String DEFAULT_REGISTRY_ADDR = "127.0.0.1:10000";
    private static final long DEFAULT_COMBINE_TIMEOUT_MS = 30_000L;

    private final String actorId;
    private final String registryAddr;
    private final Path functionFile;
    private final Path artifactPath;
    private final Path conditionFunctionsDir;
    private final FunctionInvoker.Kind nodeKind;
    private final long combineTimeoutMs;

    private ActorConfig(String actorId, String registryAddr, Path functionFile,
                         Path artifactPath, Path conditionFunctionsDir, FunctionInvoker.Kind nodeKind,
                         long combineTimeoutMs) {
        this.actorId = actorId;
        this.registryAddr = registryAddr;
        this.functionFile = functionFile;
        this.artifactPath = artifactPath;
        this.conditionFunctionsDir = conditionFunctionsDir;
        this.nodeKind = nodeKind;
        this.combineTimeoutMs = combineTimeoutMs;
    }

    /**
     * 从环境变量解析配置，校验必填项。
     *
     * @return 解析后的配置
     * @throws IllegalArgumentException 若必填项未设置或无效
     */
    public static ActorConfig fromEnvironment() {
        String actorId = System.getenv(ENV_ACTOR_ID);
        if (actorId == null || actorId.isBlank()) {
            throw new IllegalArgumentException("环境变量 " + ENV_ACTOR_ID + " 未设置");
        }

        String registryAddr = System.getenv(ENV_ACTOR_REGISTRY_ADDR);
        if (registryAddr == null || registryAddr.isBlank()) {
            registryAddr = DEFAULT_REGISTRY_ADDR;
        }

        String functionFileStr = System.getenv(ENV_ACTOR_FUNCTION_FILE);
        if (functionFileStr == null || functionFileStr.isBlank()) {
            throw new IllegalArgumentException("环境变量 " + ENV_ACTOR_FUNCTION_FILE + " 未设置");
        }
        Path functionFile = Path.of(functionFileStr);

        Path artifactPath = null;
        String artifactPathStr = System.getenv(ENV_ARTIFACT_PATH);
        if (artifactPathStr != null && !artifactPathStr.isBlank()) {
            artifactPath = Path.of(artifactPathStr);
        }

        Path conditionFunctionsDir = null;
        String conditionDirStr = System.getenv(ENV_CONDITION_FUNCTIONS_DIR);
        if (conditionDirStr != null && !conditionDirStr.isBlank()) {
            conditionFunctionsDir = Path.of(conditionDirStr);
        }

        FunctionInvoker.Kind nodeKind = parseNodeKind(System.getenv(ENV_NODE_KIND));

        long combineTimeoutMs = DEFAULT_COMBINE_TIMEOUT_MS;
        String timeoutStr = System.getenv(ENV_COMBINE_TIMEOUT_MS);
        if (timeoutStr != null && !timeoutStr.isBlank()) {
            try {
                combineTimeoutMs = Long.parseLong(timeoutStr.trim());
                if (combineTimeoutMs <= 0) combineTimeoutMs = DEFAULT_COMBINE_TIMEOUT_MS;
            } catch (NumberFormatException ignored) {
            }
        }

        return new ActorConfig(actorId, registryAddr, functionFile, artifactPath, conditionFunctionsDir, nodeKind, combineTimeoutMs);
    }

    /** 解析 IARNET_NODE_KIND 环境变量（如 NODE_KIND_INPUT），未设置或无效时返回 null，由 FunctionInvoker 回退到元数据推断。 */
    private static FunctionInvoker.Kind parseNodeKind(String value) {
        if (value == null || value.isBlank()) return null;
        return switch (value.trim().toUpperCase()) {
            case "NODE_KIND_INPUT" -> FunctionInvoker.Kind.INPUT;
            case "NODE_KIND_TASK" -> FunctionInvoker.Kind.TASK;
            case "NODE_KIND_OUTPUT" -> FunctionInvoker.Kind.OUTPUT;
            case "NODE_KIND_COMBINE" -> FunctionInvoker.Kind.COMBINE;
            default -> null;
        };
    }

    public String getActorId() {
        return actorId;
    }

    public String getRegistryAddr() {
        return registryAddr;
    }

    public Path getFunctionFile() {
        return functionFile;
    }

    /** 可选，用户 JAR 路径。 */
    public Path getArtifactPath() {
        return artifactPath;
    }

    /** 可选，条件函数目录。 */
    public Path getConditionFunctionsDir() {
        return conditionFunctionsDir;
    }

    public boolean hasArtifact() {
        return artifactPath != null;
    }

    public boolean hasConditionFunctions() {
        return conditionFunctionsDir != null;
    }

    /** 节点类型（由 Provider 通过 IARNET_NODE_KIND 传入），可为 null 表示由 FunctionInvoker 根据描述符推断。 */
    public FunctionInvoker.Kind getNodeKind() {
        return nodeKind;
    }

    /** Combine 节点两路输入缓冲超时（毫秒），由 IARNET_COMBINE_TIMEOUT_MS 配置，默认 30000。 */
    public long getCombineTimeoutMs() {
        return combineTimeoutMs;
    }
}
