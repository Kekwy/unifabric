package com.kekwy.unifabric.adapter.artifact;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 管理 artifact 在设备上的本地存储。
 */
public class ArtifactStore {

    private static final Logger log = LoggerFactory.getLogger(ArtifactStore.class);

    private final Path baseDir;

    public ArtifactStore(Path baseDir) {
        this.baseDir = baseDir;
        try {
            Files.createDirectories(baseDir);
        } catch (java.nio.file.AccessDeniedException e) {
            throw new RuntimeException(
                "无法创建 artifact 存储目录（无写权限）: " + baseDir
                    + "。请配置 unifabric.adapter.artifact-dir 为有写权限的目录，如 ~/.unifabric-adapter/artifacts", e);
        } catch (IOException e) {
            throw new RuntimeException("无法创建 artifact 存储目录: " + baseDir, e);
        }
    }

    /**
     * 将输入流写入到 artifactId 对应的文件中。
     *
     * @return 文件在设备上的绝对路径
     */
    public Path store(String artifactId, String fileName, InputStream data) throws IOException {
        Path artifactDir = baseDir.resolve(artifactId);
        Files.createDirectories(artifactDir);

        String targetName = (fileName != null && !fileName.isBlank()) ? fileName : artifactId;
        Path targetFile = artifactDir.resolve(targetName);

        try (OutputStream out = Files.newOutputStream(targetFile)) {
            data.transferTo(out);
        }

        log.info("Artifact 已存储: artifactId={}, path={}", artifactId, targetFile);
        return targetFile;
    }

    /**
     * 获取 artifact 所在目录。
     */
    public Path getArtifactDir(String artifactId) {
        return baseDir.resolve(artifactId);
    }

    /**
     * 检查 artifact 是否已存在。
     */
    public boolean exists(String artifactId) {
        return Files.isDirectory(baseDir.resolve(artifactId));
    }

    private static final String FUNCTIONS_SUBDIR = "_functions";

    /**
     * 获取该 Actor 的函数目录（主 function.pb 与 conditions/ 所在目录），用于部署时挂载。
     */
    public Path getActorFunctionDir(String actorId) {
        return baseDir.resolve(FUNCTIONS_SUBDIR).resolve(actorId);
    }

    /**
     * 将函数描述（Proto 二进制）写入 Actor 专属文件，供部署时挂载到容器。
     *
     * @param actorId  Actor ID
     * @param descriptorBytes FunctionDescriptor 的 proto 序列化字节
     * @return 写入后的文件路径（主机路径，用于 Docker bind mount）
     */
    public Path storeFunctionDescriptor(String actorId, byte[] descriptorBytes) throws IOException {
        Path dir = baseDir.resolve(FUNCTIONS_SUBDIR).resolve(actorId);
        Files.createDirectories(dir);
        Path file = dir.resolve("function.pb");
        Files.write(file, descriptorBytes != null ? descriptorBytes : new byte[0]);
        log.info("函数描述已写入: actorId={}, path={}", actorId, file);
        return file;
    }

    private static final String CONDITIONS_SUBDIR = "conditions";

    /**
     * 将条件函数描述（Proto 二进制）按 output_port 写入，供部署时挂载到容器。
     *
     * @param actorId       Actor ID
     * @param outputPort    输出端口号（1, 2, 3...）
     * @param descriptorBytes FunctionDescriptor 的 proto 序列化字节
     * @return 写入后的文件路径（主机路径，用于 Docker bind mount）
     */
    public Path storeConditionFunction(String actorId, int outputPort, byte[] descriptorBytes) throws IOException {
        Path dir = baseDir.resolve(FUNCTIONS_SUBDIR).resolve(actorId).resolve(CONDITIONS_SUBDIR);
        Files.createDirectories(dir);
        Path file = dir.resolve("condition_port_" + outputPort + ".pb");
        Files.write(file, descriptorBytes != null ? descriptorBytes : new byte[0]);
        log.info("条件函数已写入: actorId={}, outputPort={}, path={}", actorId, outputPort, file);
        return file;
    }
}
