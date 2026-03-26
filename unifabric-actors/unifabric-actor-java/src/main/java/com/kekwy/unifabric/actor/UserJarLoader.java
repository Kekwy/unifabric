package com.kekwy.unifabric.actor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectStreamClass;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/**
 * 从用户 JAR（IARNET_ARTIFACT_PATH）创建 ClassLoader，用于反序列化用户函数。
 * 用户 JAR 通常包含 SDK 依赖，故反序列化时能解析 InputFunction、TaskFunction 等接口。
 */
public final class UserJarLoader {

    private static final Logger log = LoggerFactory.getLogger(UserJarLoader.class);

    private final ClassLoader classLoader;

    private UserJarLoader(ClassLoader classLoader) {
        this.classLoader = classLoader;
    }

    /**
     * 从指定 JAR 路径创建 ClassLoader。若路径为 null 或不存在，返回使用当前线程 context class loader 的包装。
     *
     * @param artifactPath 用户 JAR 路径（可为 null）
     * @return 非 null
     */
    public static UserJarLoader create(Path artifactPath) throws IOException {
        if (artifactPath == null || !Files.isRegularFile(artifactPath)) {
            log.info("无用户 artifact 或路径无效，使用默认 ClassLoader");
            ClassLoader context = Thread.currentThread().getContextClassLoader();
            return new UserJarLoader(context != null ? context : UserJarLoader.class.getClassLoader());
        }

        URL jarUrl = artifactPath.toUri().toURL();
        URLClassLoader loader = new URLClassLoader(
                new URL[]{jarUrl},
                UserJarLoader.class.getClassLoader()
        );
        log.info("已加载用户 JAR: {}", artifactPath);
        return new UserJarLoader(loader);
    }

    /**
     * 使用本 ClassLoader 反序列化对象（用于还原用户函数）。
     *
     * @param bytes 序列化字节（如 FunctionDescriptor.serialized_function）
     * @return 反序列化后的对象
     */
    @SuppressWarnings("unchecked")
    public <T> T deserialize(byte[] bytes) throws IOException, ClassNotFoundException {
        Objects.requireNonNull(bytes, "bytes");
        try (ByteArrayInputStream bis = new ByteArrayInputStream(bytes);
             ObjectInputStream ois = new ClassLoaderObjectInputStream(bis, classLoader)) {
            return (T) ois.readObject();
        }
    }

    public ClassLoader getClassLoader() {
        return classLoader;
    }

    /**
     * 使用指定 ClassLoader 解析类的 ObjectInputStream，用于在用户 JAR 的 ClassLoader 下反序列化。
     */
    private static final class ClassLoaderObjectInputStream extends ObjectInputStream {
        private final ClassLoader classLoader;

        ClassLoaderObjectInputStream(InputStream in, ClassLoader classLoader) throws IOException {
            super(in);
            this.classLoader = classLoader;
        }

        @Override
        protected Class<?> resolveClass(ObjectStreamClass desc) throws IOException, ClassNotFoundException {
            try {
                return Class.forName(desc.getName(), false, classLoader);
            } catch (ClassNotFoundException e) {
                return super.resolveClass(desc);
            }
        }
    }
}
