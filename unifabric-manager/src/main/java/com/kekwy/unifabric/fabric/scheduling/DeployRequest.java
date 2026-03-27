package com.kekwy.unifabric.fabric.scheduling;

import com.kekwy.unifabric.proto.common.ResourceSpec;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * 管理节点侧调度部署请求（域内入口与跨域 RPC 转换共用）。
 */
public final class DeployRequest {

    private final String requestId;
    private final String instanceId;
    private final ResourceSpec demand;
    private final String image;
    private final List<String> requiredTags;
    private final String providerTypeConstraint;
    private final Map<String, String> env;
    private final Map<String, String> labels;
    private final String artifactUrl;
    private final String originatingDomainId;
    private final Instant createdAt;
    private final int retryCount;

    private DeployRequest(Builder b) {
        this.requestId = Objects.requireNonNull(b.requestId, "requestId");
        this.instanceId = b.instanceId != null && !b.instanceId.isBlank() ? b.instanceId : UUID.randomUUID().toString();
        this.demand = b.demand != null ? b.demand : ResourceSpec.getDefaultInstance();
        this.image = b.image != null ? b.image : "";
        this.requiredTags = b.requiredTags != null ? List.copyOf(b.requiredTags) : List.of();
        this.providerTypeConstraint = b.providerTypeConstraint != null ? b.providerTypeConstraint : "";
        this.env = Collections.unmodifiableMap(new LinkedHashMap<>(b.env != null ? b.env : Map.of()));
        this.labels = Collections.unmodifiableMap(new LinkedHashMap<>(b.labels != null ? b.labels : Map.of()));
        this.artifactUrl = b.artifactUrl != null ? b.artifactUrl : "";
        this.originatingDomainId = b.originatingDomainId != null ? b.originatingDomainId : "";
        this.createdAt = b.createdAt != null ? b.createdAt : Instant.now();
        this.retryCount = b.retryCount;
    }

    public String getRequestId() {
        return requestId;
    }

    public String getInstanceId() {
        return instanceId;
    }

    public ResourceSpec getDemand() {
        return demand;
    }

    public String getImage() {
        return image;
    }

    public List<String> getRequiredTags() {
        return requiredTags;
    }

    /** 非空时表示仅匹配该 {@link com.kekwy.unifabric.fabric.provider.ProviderInfo#getProviderType()} */
    public String getProviderTypeConstraint() {
        return providerTypeConstraint;
    }

    public Map<String, String> getEnv() {
        return env;
    }

    public Map<String, String> getLabels() {
        return labels;
    }

    public String getArtifactUrl() {
        return artifactUrl;
    }

    public String getOriginatingDomainId() {
        return originatingDomainId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public int getRetryCount() {
        return retryCount;
    }

    public DeployRequest withRetryIncrement() {
        return builder(this).retryCount(retryCount + 1).build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static Builder builder(DeployRequest copy) {
        return new Builder()
                .requestId(copy.requestId)
                .instanceId(copy.instanceId)
                .demand(copy.demand)
                .image(copy.image)
                .requiredTags(copy.requiredTags)
                .providerTypeConstraint(copy.providerTypeConstraint)
                .env(copy.env)
                .labels(copy.labels)
                .artifactUrl(copy.artifactUrl)
                .originatingDomainId(copy.originatingDomainId)
                .createdAt(copy.createdAt)
                .retryCount(copy.retryCount);
    }

    public static final class Builder {
        private String requestId = UUID.randomUUID().toString();
        private String instanceId;
        private ResourceSpec demand;
        private String image = "";
        private List<String> requiredTags = List.of();
        private String providerTypeConstraint = "";
        private Map<String, String> env = Map.of();
        private Map<String, String> labels = Map.of();
        private String artifactUrl = "";
        private String originatingDomainId = "";
        private Instant createdAt;
        private int retryCount;

        public Builder requestId(String requestId) {
            this.requestId = requestId;
            return this;
        }

        public Builder instanceId(String instanceId) {
            this.instanceId = instanceId;
            return this;
        }

        public Builder demand(ResourceSpec demand) {
            this.demand = demand;
            return this;
        }

        public Builder image(String image) {
            this.image = image;
            return this;
        }

        public Builder requiredTags(List<String> requiredTags) {
            this.requiredTags = requiredTags;
            return this;
        }

        public Builder providerTypeConstraint(String providerTypeConstraint) {
            this.providerTypeConstraint = providerTypeConstraint;
            return this;
        }

        public Builder env(Map<String, String> env) {
            this.env = env;
            return this;
        }

        public Builder labels(Map<String, String> labels) {
            this.labels = labels;
            return this;
        }

        public Builder artifactUrl(String artifactUrl) {
            this.artifactUrl = artifactUrl;
            return this;
        }

        public Builder originatingDomainId(String originatingDomainId) {
            this.originatingDomainId = originatingDomainId;
            return this;
        }

        public Builder createdAt(Instant createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        public Builder retryCount(int retryCount) {
            this.retryCount = retryCount;
            return this;
        }

        public DeployRequest build() {
            return new DeployRequest(this);
        }
    }
}
