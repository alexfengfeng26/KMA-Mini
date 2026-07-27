package com.kma.knowledge.storage;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.kma.knowledge.config.KnowledgeProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.LinkOption;
import java.util.List;
import java.util.stream.Stream;
import java.security.MessageDigest;
import java.util.HexFormat;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "knowledge.storage", name = "type", havingValue = "local", matchIfMissing = true)
public class LocalKnowledgeStorage implements KnowledgeStorage {
    private final KnowledgeProperties properties;

    @Override
    public String store(String spaceCode, String filename, InputStream content) throws IOException {
        String extension = FileUtil.extName(filename);
        String storedName = IdUtil.simpleUUID() + (StrUtil.isBlank(extension) ? "" : "." + extension);
        Path base = Path.of(properties.getStorage().getPath()).toAbsolutePath().normalize();
        Path directory = base.resolve(safeSegment(spaceCode)).normalize();
        if (!directory.startsWith(base)) {
            throw new IOException("非法知识空间存储路径");
        }
        Files.createDirectories(directory);
        Path target = directory.resolve(storedName);
        Files.copy(content, target, StandardCopyOption.REPLACE_EXISTING);
        return target.toString();
    }

    @Override
    public InputStream open(String location) throws IOException {
        return Files.newInputStream(validateLocation(location));
    }

    @Override
    public void delete(String location) throws IOException {
        if (location != null && !location.isBlank()) {
            Files.deleteIfExists(validateLocation(location));
        }
    }

    @Override
    public StorageObjectMetadata inspect(String location) throws IOException {
        Path path = validateLocation(location);
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(path)) {
            throw new IOException("存储对象不存在或不是普通文件");
        }
        return metadata(path);
    }

    @Override
    public List<StorageObjectMetadata> list(int limit) throws IOException {
        Path base = Path.of(properties.getStorage().getPath()).toAbsolutePath().normalize();
        if (!Files.exists(base)) return List.of();
        int bounded = Math.max(1, Math.min(limit, 10_000));
        try (Stream<Path> paths = Files.walk(base)) {
            return paths.filter(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
                .filter(path -> !Files.isSymbolicLink(path)).limit(bounded)
                .map(path -> metadata(path)).toList();
        }
    }

    private StorageObjectMetadata metadata(Path path) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream input = Files.newInputStream(path)) {
                byte[] buffer = new byte[8192];
                for (int read; (read = input.read(buffer)) >= 0; ) if (read > 0) digest.update(buffer, 0, read);
            }
            return new StorageObjectMetadata(path.toString(), Files.size(path),
                HexFormat.of().formatHex(digest.digest()), "SHA-256");
        } catch (Exception ex) {
            throw new IllegalStateException("无法读取本地存储对象元数据", ex);
        }
    }

    private String safeSegment(String value) {
        if (value == null || !value.matches("[A-Za-z0-9_-]{1,64}")) {
            throw new IllegalArgumentException("非法知识空间编码");
        }
        return value;
    }

    private Path validateLocation(String location) throws IOException {
        if (location == null || location.isBlank()) {
            throw new IOException("存储位置不能为空");
        }
        Path base = Path.of(properties.getStorage().getPath()).toAbsolutePath().normalize();
        Path resolved = Path.of(location).toAbsolutePath().normalize();
        if (!resolved.startsWith(base)) {
            throw new IOException("非法本地存储位置");
        }
        if (Files.isSymbolicLink(resolved)) throw new IOException("拒绝访问符号链接存储对象");
        return resolved;
    }
}
