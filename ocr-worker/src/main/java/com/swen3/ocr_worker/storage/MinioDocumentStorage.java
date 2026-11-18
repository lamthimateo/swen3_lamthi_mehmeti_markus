package com.swen3.ocr_worker.storage;

import com.swen3.ocr_worker.config.MinioProperties;
import io.minio.GetObjectArgs;
import io.minio.GetObjectResponse;
import io.minio.MinioClient;
import io.minio.errors.ErrorResponseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

@Component
public class MinioDocumentStorage implements DocumentStorage {

    private static final Logger log = LoggerFactory.getLogger(MinioDocumentStorage.class);

    private final MinioClient client;
    private final MinioProperties properties;

    public MinioDocumentStorage(MinioClient client, MinioProperties properties) {
        this.client = client;
        this.properties = properties;
    }

    @Override
    public Path download(String objectKey) {
        Path target = null;
        try (GetObjectResponse in = client.getObject(
                GetObjectArgs.builder().bucket(properties.bucket()).object(objectKey).build())) {
            target = Files.createTempFile("document-", ".pdf");
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            log.debug("Downloaded '{}/{}' to {}", properties.bucket(), objectKey, target);
            return target;
        } catch (ErrorResponseException e) {
            deleteQuietly(target);
            String code = e.errorResponse().code();
            if ("NoSuchKey".equals(code) || "NoSuchBucket".equals(code)) {
                throw new DocumentNotFoundException(
                        "object '" + objectKey + "' not found in bucket '" + properties.bucket() + "' (" + code + ")");
            }
            throw new StorageException("MinIO rejected the download of '" + objectKey + "' (" + code + ")", e);
        } catch (Exception e) {
            deleteQuietly(target);
            throw new StorageException("could not download '" + objectKey + "' from MinIO", e);
        }
    }

    private static void deleteQuietly(Path file) {
        if (file == null) {
            return;
        }
        try {
            Files.deleteIfExists(file);
        } catch (IOException ignored) {
            // temp file, the OS cleans it up eventually
        }
    }
}
