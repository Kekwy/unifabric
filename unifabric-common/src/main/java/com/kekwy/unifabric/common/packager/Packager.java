package com.kekwy.unifabric.common.packager;

import java.nio.file.Path;

/**
 * 将指定路径下的源码打包为可分发的 artifact 文件。
 */
public interface Packager {

    /**
     * 将 sourcePath 指向的源码打包，产物写入 outputDir。
     *
     * @param sourcePath 源码路径（Java: Maven 项目目录, Python: 源码目录, Go: Go 项目目录）
     * @param outputDir  产物输出目录
     * @return 生成的 artifact 文件路径
     */
    Path pack(Path sourcePath, Path outputDir);
}
