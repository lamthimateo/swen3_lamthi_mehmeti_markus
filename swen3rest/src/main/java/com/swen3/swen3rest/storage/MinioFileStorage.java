package com.swen3.swen3rest.storage;

import com.swen3.swen3rest.config.MinioProperties;
import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.InputStream;

@Component
public class MinioFileStorage implements FileStorage {

    private static final Logger log = LoggerFactory.getLogger(MinioFileStorage.class);

    private final MinioClient client;
    private final MinioProperties properties;
    private volatile boolean bucketReady;

    public MinioFileStorage(MinioClient client, MinioProperties properties) {
        this.client = client;
        this.properties = properties;
    }

    @Override
    public void store(String key, InputStream content, long size, String contentType) {
        try {
            ensureBucket();
            client.putObject(PutObjectArgs.builder()
                    .bucket(properties.bucket())
                    .object(key)
                    .stream(content, size, -1)   // -1: let the SDK pick the part size, the total size is known
                    .contentType(contentType == null ? "application/octet-stream" : contentType)
                    .build());
            log.info("Stored '{}/{}' in MinIO ({} bytes)", properties.bucket(), key, size);
        } catch (Exception e) {
            log.error("Could not store '{}/{}' in MinIO", properties.bucket(), key, e);
            throw new StorageException("could not store '" + key + "' in MinIO", e);
        }
    }

    @Override
    public void deleteQuietly(String key) {
        try {
            client.removeObject(RemoveObjectArgs.builder().bucket(properties.bucket()).object(key).build());
            log.info("Removed '{}/{}' from MinIO", properties.bucket(), key);
        } catch (Exception e) {
            log.warn("Could not remove '{}/{}' from MinIO: {}", properties.bucket(), key, e.getMessage());
        }
    }

    /** Creates the bucket on first use, so no manual MinIO setup is needed. */
    private synchronized void ensureBucket() throws Exception {
        if (bucketReady) {
            return;
        }
        if (!client.bucketExists(BucketExistsArgs.builder().bucket(properties.bucket()).build())) {
            client.makeBucket(MakeBucketArgs.builder().bucket(properties.bucket()).build());
            log.info("Created MinIO bucket '{}'", properties.bucket());
        }
        bucketReady = true;
    }
}
