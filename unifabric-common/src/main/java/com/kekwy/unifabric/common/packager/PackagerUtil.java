package com.kekwy.unifabric.common.packager;

import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

final class PackagerUtil {

    private PackagerUtil() {
    }

    static void runCommand(String[] command, Path workDir, Path logFile) {
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.directory(workDir.toFile());
        pb.redirectErrorStream(true);
        pb.redirectOutput(ProcessBuilder.Redirect.appendTo(logFile.toFile()));

        try {
            Process process = pb.start();
            boolean finished = process.waitFor(15, TimeUnit.MINUTES);
            if (!finished) {
                process.destroyForcibly();
                throw new IllegalStateException(
                        "命令超时: " + String.join(" ", command));
            }
            if (process.exitValue() != 0) {
                throw new IllegalStateException(
                        "命令失败, exitCode=" + process.exitValue()
                                + ", cmd=" + String.join(" ", command)
                                + ", 日志: " + logFile);
            }
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new RuntimeException("执行命令异常: " + String.join(" ", command), e);
        }
    }
}
