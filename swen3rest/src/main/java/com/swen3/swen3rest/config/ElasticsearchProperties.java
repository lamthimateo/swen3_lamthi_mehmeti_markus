package com.swen3.swen3rest.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Where the OCR worker indexes document text and where the search endpoint reads it from. */
@ConfigurationProperties(prefix = "app.elasticsearch")
public record ElasticsearchProperties(String url, String index) {
}
