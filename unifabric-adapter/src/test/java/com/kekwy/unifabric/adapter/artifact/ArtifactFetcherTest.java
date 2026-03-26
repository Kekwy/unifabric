package com.kekwy.unifabric.adapter.artifact;

import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.http.Method;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Adapter 侧 artifact 拉取功能单元测试。
 * <p>
 * 使用本地真实 MinIO（{@code http://localhost:9002}）上传测试文件并生成预签名 URL，
 * 再由 ArtifactFetcher 拉取，验证落盘与按 artifact_url 去重。
 * 运行前请启动 MinIO 且 API 端口为 9002，例如：{@code cd deploy/minio && docker compose up -d} 并映射 9002。
 */
@DisplayName("ArtifactFetcher 拉取 artifact（真实 MinIO）")
class ArtifactFetcherTest {

    private static final String MINIO_ENDPOINT = "http://localhost:9002";
    private static final String TEST_BUCKET = "iarnet-artifacts-test";

    @TempDir
    Path tempDir;

    private ArtifactStore artifactStore;
    private ArtifactFetcher fetcher;
    private MinioClient minioClient;

    @BeforeEach
    void setUp() throws Exception {
        artifactStore = new ArtifactStore(tempDir);
        fetcher = new ArtifactFetcher(artifactStore);

        minioClient = MinioClient.builder()
                .endpoint(MINIO_ENDPOINT)
                .credentials("minioadmin", "minioadmin")
                .build();

        if (!minioClient.bucketExists(io.minio.BucketExistsArgs.builder().bucket(TEST_BUCKET).build())) {
            minioClient.makeBucket(MakeBucketArgs.builder().bucket(TEST_BUCKET).build());
        }
    }

    private String uploadToMinio(String objectKey, byte[] content) throws Exception {
        minioClient.putObject(
                PutObjectArgs.builder()
                        .bucket(TEST_BUCKET)
                        .object(objectKey)
                        .stream(new ByteArrayInputStream(content), content.length, -1)
                        .contentType("application/octet-stream")
                        .build());
        return minioClient.getPresignedObjectUrl(
                GetPresignedObjectUrlArgs.builder()
                        .method(Method.GET)
                        .bucket(TEST_BUCKET)
                        .object(objectKey)
                        .expiry(5, TimeUnit.MINUTES)
                        .build());
    }

    @Test
    @DisplayName("fetch：从 MinIO 预签名 URL 拉取应落盘并返回本地路径")
    void fetch_shouldDownloadFromMinioAndStore() throws Exception {
        byte[] body = "fake-jar-content-for-adapter-test".getBytes();
        String objectKey = "artifacts/node1/app.jar";
        String presignedUrl = uploadToMinio(objectKey, body);

        Path path = fetcher.fetch("art-001", presignedUrl);

        assertNotNull(path);
        assertTrue(Files.isRegularFile(path));
        assertEquals("app.jar", path.getFileName().toString());
        assertArrayEquals(body, Files.readAllBytes(path));
        assertTrue(path.startsWith(tempDir));
    }

    @Test
    @DisplayName("fetch：同一 artifact_url 再次拉取应命中缓存返回相同路径")
    void fetch_sameArtifactUrl_shouldReturnCachedPath() throws Exception {
        byte[] body = "cached-content".getBytes();
        String presignedUrl = uploadToMinio("path/to/file.jar", body);

        Path first = fetcher.fetch("actor-1", presignedUrl);
        Path second = fetcher.fetch("actor-2", presignedUrl);

        assertNotNull(first);
        assertNotNull(second);
        assertEquals(first, second);
        assertTrue(Files.exists(first));
        assertArrayEquals(body, Files.readAllBytes(first));
    }

    @Test
    @DisplayName("fetch：artifactUrl 为空应抛出 IllegalArgumentException")
    void fetch_blankArtifactUrl_shouldThrow() {
        assertThrows(IllegalArgumentException.class, () ->
                fetcher.fetch("id", null));
        assertThrows(IllegalArgumentException.class, () ->
                fetcher.fetch("id", ""));
        assertThrows(IllegalArgumentException.class, () ->
                fetcher.fetch("id", "   "));
    }

    @Test
    @DisplayName("fetch：MinIO 上不存在的对象应抛出 IOException")
    void fetch_nonexistentObject_shouldThrow() throws Exception {
        String url = minioClient.getPresignedObjectUrl(
                GetPresignedObjectUrlArgs.builder()
                        .method(Method.GET)
                        .bucket(TEST_BUCKET)
                        .object("nonexistent/key-404")
                        .expiry(1, TimeUnit.MINUTES)
                        .build());

        assertThrows(IOException.class, () -> fetcher.fetch("fail-id", url));
    }
}
