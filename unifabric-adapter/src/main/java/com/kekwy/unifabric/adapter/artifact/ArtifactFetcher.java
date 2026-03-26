package com.kekwy.unifabric.adapter.artifact;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 按 artifact_url 拉取 artifact 到本地 ArtifactStore，同一 URL 仅拉取一次，多 actor 复用。
 */
public class ArtifactFetcher {

    private static final Logger log = LoggerFactory.getLogger(ArtifactFetcher.class);

    private final ArtifactStore artifactStore;
    private final HttpClient httpClient;

    /** 按 artifactUrl 去重：同一 URL 只拉取一次，多个 actorId 复用同一 path */
    private final Map<String, Path> cacheByUrl = new ConcurrentHashMap<>();
    private final Map<String, CompletableFuture<Path>> inFlightByUrl = new ConcurrentHashMap<>();

    public ArtifactFetcher(ArtifactStore artifactStore) {
        this.artifactStore = artifactStore;
        this.httpClient = HttpClient.newBuilder().build();
    }

    /**
     * 拉取 artifact：同一 artifactUrl 仅拉取一次，已缓存或正在拉取则复用。
     *
     * @param actorId    当前 actor 的 id（仅用于日志），不参与缓存 key
     * @param artifactUrl 制品 URL，作为去重 key
     */
    public Path fetch(String actorId, String artifactUrl) throws IOException {
        if (artifactUrl == null || artifactUrl.isBlank()) {
            throw new IllegalArgumentException("artifactUrl 不能为空");
        }

        Path cached = cacheByUrl.get(artifactUrl);
        if (cached != null && java.nio.file.Files.exists(cached)) {
            log.debug("Artifact 命中缓存: artifactUrl={}, actorId={}", artifactUrl, actorId);
            return cached;
        }

        CompletableFuture<Path> future = inFlightByUrl.computeIfAbsent(artifactUrl, k ->
                CompletableFuture.supplyAsync(() -> {
                    try {
                        return doFetch(artifactUrl);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }).whenComplete((path, ex) -> {
                    if (path != null) cacheByUrl.put(artifactUrl, path);
                    inFlightByUrl.remove(artifactUrl);
                }));

        try {
            return future.join();
        } catch (Exception e) {
            inFlightByUrl.remove(artifactUrl);
            if (e.getCause() instanceof IOException ioe) throw ioe;
            throw new IOException("拉取 artifact 失败: " + artifactUrl, e);
        }
    }

    /** 由 URL 生成稳定存储目录 id，同一 URL 始终对应同一 id */
    private static String storageIdFromUrl(String artifactUrl) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(artifactUrl.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return "url-" + HexFormat.of().formatHex(hash).substring(0, 32);
        } catch (NoSuchAlgorithmException e) {
            return "url-" + Integer.toHexString(artifactUrl.hashCode());
        }
    }

    private Path doFetch(String artifactUrl) throws IOException {
        String storageId = storageIdFromUrl(artifactUrl);
        if (artifactStore.exists(storageId)) {
            Path existing = artifactStore.getArtifactDir(storageId).resolve(fileNameFromUrl(artifactUrl));
            if (java.nio.file.Files.exists(existing)) {
                log.info("Artifact 已存在，跳过拉取: storageId={}", storageId);
                return existing;
            }
        }

        log.info("开始拉取 artifact: artifactUrl={}", artifactUrl);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(artifactUrl))
                .GET()
                .build();

        HttpResponse<InputStream> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        if (response.statusCode() != 200) {
            throw new IOException("拉取失败 HTTP " + response.statusCode() + ": " + artifactUrl);
        }

        String fileName = fileNameFromUrl(artifactUrl);
        Path path = artifactStore.store(storageId, fileName, response.body());
        log.info("Artifact 拉取完成: storageId={}, path={}", storageId, path);
        return path;
    }

    private static String fileNameFromUrl(String url) {
        try {
            String path = URI.create(url).getPath();
            if (path != null && path.contains("/")) {
                String name = path.substring(path.lastIndexOf('/') + 1);
                if (!name.isBlank()) return name;
            }
        } catch (Exception ignored) { }
        return "artifact";
    }
}
