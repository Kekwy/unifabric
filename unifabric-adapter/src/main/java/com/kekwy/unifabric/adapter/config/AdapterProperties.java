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
    private String artifactDir = System.getProperty("user.home") + "/.unifabric-adapter/artifacts";
    private DockerConfig docker = new DockerConfig();
    private K8sConfig k8s = new K8sConfig();
    /** 论文 3.5：TCP 代理监听端口区间；min=0 表示仅用系统临时端口 */
    private Connectivity connectivity = new Connectivity();

    @Data
    public static class Connectivity {
        private int proxyPortMin = 0;
        private int proxyPortMax = 0;
    }

    @Data
    public static class Registry {
        private String host = "127.0.0.1";
        private int port = 9090;
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
}
