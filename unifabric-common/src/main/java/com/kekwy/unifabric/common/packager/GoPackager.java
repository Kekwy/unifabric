package com.kekwy.unifabric.common.packager;

import com.kekwy.unifabric.common.Constants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Go 源码打包器：在指定目录执行 {@code go build}，构建为可执行文件。
 */
public class GoPackager implements Packager {

    private static final Logger log = LoggerFactory.getLogger(GoPackager.class);

    @Override
    public Path pack(Path sourcePath, Path outputDir) {
        Path goMod = sourcePath.resolve("go.mod");
        if (!Files.exists(goMod)) {
            throw new IllegalStateException("未找到 go.mod: " + sourcePath);
        }

        log.info("开始 Go 构建: {}", sourcePath);
        Path artifactPath = outputDir.resolve(Constants.ARTIFACT_FILENAME_GO);
        Path logFile = outputDir.resolve("go-build.log");
        PackagerUtil.runCommand(new String[]{
                "go", "build", "-o", artifactPath.toAbsolutePath().toString(), "."
        }, sourcePath, logFile);

        log.info("Go artifact 已生成: {}", artifactPath);
        return artifactPath;
    }
}
