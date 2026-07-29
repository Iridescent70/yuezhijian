package com.yuezhijian.server.file;

import com.yuezhijian.server.common.ResourceNotFoundException;
import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import java.io.ByteArrayInputStream;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("sqlserver")
@ConditionalOnProperty(name = "app.file-storage.provider", havingValue = "minio")
public class MinioObjectStorage implements ObjectStorage {
    private final MinioClient client;
    private final String bucket;

    public MinioObjectStorage(FileStorageProperties properties) {
        if (properties.accessKey() == null || properties.accessKey().isBlank()
                || properties.secretKey() == null || properties.secretKey().isBlank()) {
            throw new IllegalStateException("MinIO访问账号和密钥未配置");
        }
        client = MinioClient.builder().endpoint(properties.endpoint())
                .credentials(properties.accessKey(), properties.secretKey()).build();
        bucket = properties.bucket();
    }

    @Override
    public void put(String objectKey, byte[] content, String contentType) {
        try {
            ensureBucket();
            client.putObject(PutObjectArgs.builder().bucket(bucket).object(objectKey)
                    .contentType(contentType)
                    .stream(new ByteArrayInputStream(content), content.length, -1).build());
        } catch (Exception exception) {
            throw new FileStorageException("对象存储写入失败", exception);
        }
    }

    @Override
    public byte[] get(String objectKey) {
        try (var input = client.getObject(GetObjectArgs.builder().bucket(bucket).object(objectKey).build())) {
            return input.readAllBytes();
        } catch (io.minio.errors.ErrorResponseException exception) {
            if ("NoSuchKey".equals(exception.errorResponse().code())) {
                throw new ResourceNotFoundException("附件文件不存在");
            }
            throw new FileStorageException("对象存储读取失败", exception);
        } catch (Exception exception) {
            throw new FileStorageException("对象存储读取失败", exception);
        }
    }

    @Override
    public void delete(String objectKey) {
        try {
            client.removeObject(RemoveObjectArgs.builder().bucket(bucket).object(objectKey).build());
        } catch (Exception exception) {
            throw new FileStorageException("对象存储清理失败", exception);
        }
    }

    private void ensureBucket() throws Exception {
        if (!client.bucketExists(BucketExistsArgs.builder().bucket(bucket).build())) {
            client.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
        }
    }
}
