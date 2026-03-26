package com.kekwy.unifabric.actor;

import com.kekwy.unifabric.proto.common.FunctionDescriptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 从文件系统加载主函数描述与条件函数描述。
 * 主函数：单个 .pb 文件；条件函数：目录下 condition_port_{N}.pb。
 */
public final class FunctionDescriptorLoader {

    private static final Logger log = LoggerFactory.getLogger(FunctionDescriptorLoader.class);
    private static final Pattern CONDITION_FILE_PATTERN = Pattern.compile("condition_port_(\\d+)\\.pb");

    private FunctionDescriptorLoader() {}

    /**
     * 从指定路径加载主函数描述符。
     *
     * @param functionFilePath function.pb 路径
     * @return FunctionDescriptor，若文件不存在或解析失败则抛出
     */
    public static FunctionDescriptor loadMain(Path functionFilePath) throws IOException {
        if (!Files.isRegularFile(functionFilePath)) {
            throw new IOException("函数描述文件不存在或不是文件: " + functionFilePath);
        }
        byte[] bytes = Files.readAllBytes(functionFilePath);
        return FunctionDescriptor.parseFrom(bytes);
    }

    /**
     * 从条件函数目录加载所有 condition_port_{N}.pb，返回 port -> FunctionDescriptor。
     *
     * @param conditionFunctionsDir 条件函数目录（如 /opt/iarnet/function/conditions）
     * @return 可能为空，不会为 null
     */
    public static Map<Integer, FunctionDescriptor> loadConditions(Path conditionFunctionsDir) throws IOException {
        Map<Integer, FunctionDescriptor> result = new HashMap<>();
        if (conditionFunctionsDir == null || !Files.isDirectory(conditionFunctionsDir)) {
            return result;
        }

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(conditionFunctionsDir, "condition_port_*.pb")) {
            for (Path file : stream) {
                String name = file.getFileName().toString();
                Matcher m = CONDITION_FILE_PATTERN.matcher(name);
                if (m.matches()) {
                    int port = Integer.parseInt(m.group(1));
                    byte[] bytes = Files.readAllBytes(file);
                    FunctionDescriptor fd = FunctionDescriptor.parseFrom(bytes);
                    result.put(port, fd);
                    log.debug("已加载条件函数: port={}, path={}", port, file);
                }
            }
        }
        return result;
    }
}
