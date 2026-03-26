package com.kekwy.unifabric.common.packager;

import com.kekwy.unifabric.common.Constants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.stream.Stream;

/**
 * Java 源码打包器：在指定目录执行 {@code mvn clean package}，
 * 选取 target/ 中最大的 JAR 作为 artifact，输出为 fat jar。
 */
public class JavaPackager implements Packager {

    private static final Logger log = LoggerFactory.getLogger(JavaPackager.class);

    @Override
    public Path pack(Path sourcePath, Path outputDir) {
        Path pomFile = sourcePath.resolve("pom.xml");
        if (!Files.exists(pomFile)) {
            throw new IllegalStateException("未找到 pom.xml: " + sourcePath);
        }

        log.info("开始 Maven 打包: {}", sourcePath);
        Path logFile = outputDir.resolve("java-build.log");
        PackagerUtil.runCommand(new String[]{"mvn", "-DskipTests=true", "clean", "package"}, sourcePath, logFile);

        Path targetDir = sourcePath.resolve("target");
        try (Stream<Path> files = Files.list(targetDir)) {
            Path jar = files
                    .filter(p -> p.toString().endsWith(".jar"))
                    .max(Comparator.comparingLong(p -> {
                        try {
                            return Files.size(p);
                        } catch (IOException e) {
                            return 0L;
                        }
                    }))
                    .orElseThrow(() -> new IllegalStateException("target 中未找到 JAR: " + targetDir));

            Path artifactPath = outputDir.resolve(Constants.ARTIFACT_FILENAME_JAVA);
            Files.copy(jar, artifactPath, StandardCopyOption.REPLACE_EXISTING);
            log.info("Java artifact 已生成: {}", artifactPath);
            return artifactPath;
        } catch (IOException e) {
            throw new RuntimeException("读取 Maven 构建产物失败", e);
        }
    }
}
