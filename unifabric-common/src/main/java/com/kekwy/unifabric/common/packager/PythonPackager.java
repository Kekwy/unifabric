package com.kekwy.unifabric.common.packager;

import com.kekwy.unifabric.common.Constants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Python 源码打包器：
 * <ol>
 *   <li>使用 conda 创建临时环境并安装 requirements.txt 中的依赖</li>
 *   <li>安装当前模块（pip install .）</li>
 *   <li>通过 {@code conda-pack} 将环境打包为 tar.xz</li>
 * </ol>
 *
 * <p>要求宿主机已安装 conda 和 conda-pack ({@code conda install conda-pack})。
 */
public class PythonPackager implements Packager {

    private static final Logger log = LoggerFactory.getLogger(PythonPackager.class);

    @Override
    public Path pack(Path sourcePath, Path outputDir) {
        Path requirementsFile = sourcePath.resolve("requirements.txt");
        if (!Files.exists(requirementsFile)) {
            throw new IllegalStateException(
                    "requirements.txt 不存在: " + sourcePath + "，Python 源码目录必须包含 requirements.txt");
        }

        String envName = "iarnet-py-" + System.currentTimeMillis();
        Path artifactPath = outputDir.resolve(Constants.ARTIFACT_FILENAME_PYTHON);
        Path logFile = outputDir.resolve("python-build.log");

        try {
            // 1. 创建 conda 环境
            log.info("创建 conda 环境: {}", envName);
            PackagerUtil.runCommand(new String[]{
                    "conda", "create", "-n", envName, "-y", "python=3.10"
            }, sourcePath, logFile);

            // 2. 安装依赖
            log.info("安装 Python 依赖: {}", requirementsFile);
            PackagerUtil.runCommand(new String[]{
                    "conda", "run", "-n", envName,
                    "pip", "install", "-r", requirementsFile.toAbsolutePath().toString()
            }, sourcePath, logFile);

            // 3. 安装当前模块
            PackagerUtil.runCommand(new String[]{
                    "conda", "run", "-n", envName,
                    "pip", "install", ".",
                    "--no-deps"
            }, sourcePath, logFile);

            // 4. 使用 conda-pack 打包为 tar.xz
            log.info("使用 conda-pack 打包环境: {}", envName);
            PackagerUtil.runCommand(new String[]{
                    "conda-pack", "-n", envName,
                    "-o", artifactPath.toAbsolutePath().toString(),
                    "--force"
            }, sourcePath, logFile);

            log.info("Python artifact 已生成: {}", artifactPath);
            return artifactPath;
        } finally {
            // 5. 清理临时 conda 环境
            try {
                PackagerUtil.runCommand(new String[]{
                        "conda", "env", "remove", "-n", envName, "-y"
                }, sourcePath, logFile);
            } catch (Exception e) {
                log.warn("清理 conda 环境失败: {}", envName, e);
            }
        }
    }
}
