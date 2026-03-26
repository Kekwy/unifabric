package com.kekwy.unifabric.adapter.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@Data
@ConfigurationProperties(prefix = "unifabric.adapter")
public class AdapterProperties {
    private String host;
    private String name;
    private String description = "";
    private String type = "docker";
    private String zone = "";
    private Registry registry = new Registry();
    private List<String> tags = List.of();
    private ResourceConfig resource = new ResourceConfig();
    /** 默认使用用户目录，避免 /tmp 在 WSL 等环境下无写权限 */
    private String artifactDir = System.getProperty("user.home") + "/.unifabric-adapter/artifacts";
    private DockerConfig docker = new DockerConfig();
    private K8sConfig k8s = new K8sConfig();
    private ActorRegistry actorRegistry = new ActorRegistry();

    // ===== 嵌套配置类 =====

    @Data
    public static class Registry {
        private String host = "127.0.0.1";
        private int port = 9090;

    }

    @Data
    public static class ResourceConfig {
        private double cpu = 4.0;
        private String memory = "8Gi";
        private double gpu = 0;

    }

    @Data
    public static class DockerConfig {
        private String host = "unix:///var/run/docker.sock";
        private String network = "";
    }

    @Data
    public static class K8sConfig {
        private String kubeconfig = "";
        private boolean inCluster = false;
        private String namespace = "default";
    }

    @Data
    public static class ActorRegistry {
        private int port = 10000;
    }
}
