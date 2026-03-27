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

    public Path getArtifactDir(String artifactId) {
        return baseDir.resolve(artifactId);
    }

    public boolean exists(String artifactId) {
        return Files.isDirectory(baseDir.resolve(artifactId));
    }
}
