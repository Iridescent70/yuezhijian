package com.yuezhijian.server.file;

import com.yuezhijian.server.common.ResourceNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("sqlserver")
@ConditionalOnProperty(name = "app.file-storage.provider", havingValue = "local", matchIfMissing = true)
public class LocalObjectStorage implements ObjectStorage {
    private final Path root;

    public LocalObjectStorage(FileStorageProperties properties) {
        root = Path.of(properties.localRoot()).toAbsolutePath().normalize();
    }

    @Override
    public void put(String objectKey, byte[] content, String contentType) {
        Path target = resolve(objectKey);
        try {
            Files.createDirectories(target.getParent());
            Files.write(target, content, StandardOpenOption.CREATE_NEW);
        } catch (IOException exception) {
            throw new FileStorageException("本地附件写入失败", exception);
        }
    }

    @Override
    public byte[] get(String objectKey) {
        try {
            Path target = resolve(objectKey);
            if (!Files.isRegularFile(target)) throw new ResourceNotFoundException("附件文件不存在");
            return Files.readAllBytes(target);
        } catch (IOException exception) {
            throw new FileStorageException("本地附件读取失败", exception);
        }
    }

    @Override
    public void delete(String objectKey) {
        try {
            Files.deleteIfExists(resolve(objectKey));
        } catch (IOException exception) {
            throw new FileStorageException("本地附件清理失败", exception);
        }
    }

    private Path resolve(String objectKey) {
        Path target = root.resolve(objectKey).normalize();
        if (!target.startsWith(root)) throw new IllegalArgumentException("附件对象键无效");
        return target;
    }
}
