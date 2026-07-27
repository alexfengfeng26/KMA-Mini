package com.kma.knowledge.storage;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.kma.knowledge.config.KnowledgeProperties;
import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.ListObjectsArgs;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.StatObjectArgs;
import io.minio.Result;
import io.minio.messages.Item;
import jakarta.annotation.PostConstruct;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** MinIO 与其他 S3 兼容对象存储实现，凭据仅从外部配置注入。 */
@Component
@ConditionalOnProperty(prefix = "knowledge.storage", name = "type", havingValue = "minio")
public class MinioKnowledgeStorage implements KnowledgeStorage {
    private static final long MULTIPART_PART_SIZE = 10L * 1024 * 1024;

    private final KnowledgeProperties properties;
    private MinioClient client;

    public MinioKnowledgeStorage(KnowledgeProperties properties) {
        this.properties = properties;
    }

    @PostConstruct
    void initialize() {
        KnowledgeProperties.StorageProperties config = properties.getStorage();
        if (!StringUtils.hasText(config.getEndpoint()) || !StringUtils.hasText(config.getAccessKey())
            || !StringUtils.hasText(config.getSecretKey()) || !StringUtils.hasText(config.getBucket())) {
            throw new IllegalStateException("MinIO 存储需要 endpoint、accessKey、secretKey 和 bucket");
        }
        client = MinioClient.builder()
            .endpoint(config.getEndpoint())
            .credentials(config.getAccessKey(), config.getSecretKey())
            .build();
        try {
            if (!client.bucketExists(BucketExistsArgs.builder().bucket(config.getBucket()).build())) {
                MakeBucketArgs.Builder builder = MakeBucketArgs.builder().bucket(config.getBucket());
                if (StringUtils.hasText(config.getRegion())) {
                    builder.region(config.getRegion());
                }
                client.makeBucket(builder.build());
            }
        } catch (Exception ex) {
            throw new IllegalStateException("无法初始化 MinIO bucket: " + config.getBucket(), ex);
        }
    }

    @Override
    public String store(String spaceCode, String filename, InputStream content) throws IOException {
        String safeSpace = safeSegment(spaceCode);
        String extension = FileUtil.extName(filename);
        LocalDate today = LocalDate.now();
        String objectName = "%s/%d/%02d/%s%s".formatted(
            safeSpace, today.getYear(), today.getMonthValue(), IdUtil.simpleUUID(),
            StrUtil.isBlank(extension) ? "" : "." + extension.toLowerCase());
        try {
            client.putObject(PutObjectArgs.builder()
                .bucket(properties.getStorage().getBucket())
                .object(objectName)
                .contentType("application/octet-stream")
                .stream(content, -1L, MULTIPART_PART_SIZE)
                .build());
            return location(objectName);
        } catch (Exception ex) {
            throw new IOException("MinIO 对象写入失败: " + objectName, ex);
        }
    }

    @Override
    public InputStream open(String location) throws IOException {
        String objectName = objectName(location);
        try {
            return client.getObject(GetObjectArgs.builder()
                .bucket(properties.getStorage().getBucket())
                .object(objectName)
                .build());
        } catch (Exception ex) {
            throw new IOException("MinIO 对象读取失败: " + objectName, ex);
        }
    }

    @Override
    public void delete(String location) throws IOException {
        if (!StringUtils.hasText(location)) {
            return;
        }
        String objectName = objectName(location);
        try {
            client.removeObject(RemoveObjectArgs.builder()
                .bucket(properties.getStorage().getBucket())
                .object(objectName)
                .build());
        } catch (Exception ex) {
            throw new IOException("MinIO 对象删除失败: " + objectName, ex);
        }
    }

    @Override
    public StorageObjectMetadata inspect(String location) throws IOException {
        String objectName = objectName(location);
        try {
            var stat = client.statObject(StatObjectArgs.builder()
                .bucket(properties.getStorage().getBucket()).object(objectName).build());
            return new StorageObjectMetadata(location(objectName), stat.size(), stat.etag(), "ETAG");
        } catch (Exception ex) {
            throw new IOException("MinIO 对象不存在或无法读取元数据: " + objectName, ex);
        }
    }

    @Override
    public List<StorageObjectMetadata> list(int limit) throws IOException {
        String prefix = "";
        int bounded = Math.max(1, Math.min(limit, 10_000));
        List<StorageObjectMetadata> objects = new ArrayList<>();
        try {
            Iterable<Result<Item>> results = client.listObjects(ListObjectsArgs.builder()
                .bucket(properties.getStorage().getBucket()).prefix(prefix).recursive(true).build());
            for (Result<Item> result : results) {
                Item item = result.get();
                objects.add(new StorageObjectMetadata(location(item.objectName()), item.size(), item.etag(), "ETAG"));
                if (objects.size() >= bounded) break;
            }
            return objects;
        } catch (Exception ex) {
            throw new IOException("MinIO 对象列表读取失败: " + prefix, ex);
        }
    }

    private String location(String objectName) {
        return "s3://" + properties.getStorage().getBucket() + "/" + objectName;
    }

    private String objectName(String location) throws IOException {
        String prefix = "s3://" + properties.getStorage().getBucket() + "/";
        if (location == null || !location.startsWith(prefix) || location.length() == prefix.length()) {
            throw new IOException("非法或跨 bucket 的对象位置");
        }
        String objectName = location.substring(prefix.length());
        if (objectName.contains("..") || objectName.startsWith("/")) {
            throw new IOException("非法对象路径");
        }
        return objectName;
    }

    private String safeSegment(String value) {
        if (value == null || !value.matches("[A-Za-z0-9_-]{1,64}")) {
            throw new IllegalArgumentException("非法知识空间编码");
        }
        return value;
    }
}
