package com.swen3.ocr_worker.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Connection settings for MinIO, bound from {@code app.minio.*} (see application.yaml). */
@ConfigurationProperties(prefix = "app.minio")
public record MinioProperties(String endpoint, String accessKey, String secretKey, String bucket) {
}
