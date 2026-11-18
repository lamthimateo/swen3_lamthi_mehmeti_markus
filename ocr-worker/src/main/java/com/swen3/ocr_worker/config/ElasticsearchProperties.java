package com.swen3.ocr_worker.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Where and into which index OCR results are stored, bound from {@code app.elasticsearch.*}. */
@ConfigurationProperties(prefix = "app.elasticsearch")
public record ElasticsearchProperties(String url, String index) {
}
