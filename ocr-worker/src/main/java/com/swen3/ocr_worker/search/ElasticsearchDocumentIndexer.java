package com.swen3.ocr_worker.search;

import com.swen3.ocr_worker.IndexJobMessage;
import com.swen3.ocr_worker.config.ElasticsearchProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Indexes documents through Elasticsearch's REST API ({@code PUT /<index>/_doc/<id>}) using the JDK HttpClient.
 * <p>
 * The document id in Elasticsearch is the database id, so indexing the same document twice (for example when a
 * message is redelivered) replaces the entry instead of creating a duplicate. The index is created on first use
 * with dynamic mapping; {@code content} becomes an analysed text field, which is what makes a search for
 * "Hello" find "Hello World".
 */
@Component
public class ElasticsearchDocumentIndexer implements DocumentIndexer {

    private static final Logger log = LoggerFactory.getLogger(ElasticsearchDocumentIndexer.class);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);

    private final ElasticsearchProperties properties;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final JsonMapper json = JsonMapper.builder().build();

    public ElasticsearchDocumentIndexer(ElasticsearchProperties properties) {
        this.properties = properties;
    }

    @Override
    public void index(IndexJobMessage message) {
        Map<String, Object> source = new LinkedHashMap<>();
        source.put("documentId", message.documentId());
        source.put("filename", message.filename() == null ? "" : message.filename());
        source.put("content", message.text() == null ? "" : message.text());
        source.put("indexedAt", Instant.now().toString());

        String base = properties.url().endsWith("/")
                ? properties.url().substring(0, properties.url().length() - 1)
                : properties.url();
        URI uri = URI.create(base + "/" + properties.index() + "/_doc/" + message.documentId());

        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(REQUEST_TIMEOUT)
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(source)))
                .build();

        try {
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                throw new IndexingException("Elasticsearch answered " + response.statusCode()
                        + " for document " + message.documentId() + ": " + abbreviate(response.body()));
            }
            log.debug("Elasticsearch stored document {} in index '{}'", message.documentId(), properties.index());
        } catch (IOException e) {
            throw new IndexingException("could not reach Elasticsearch at " + properties.url(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IndexingException("interrupted while indexing document " + message.documentId(), e);
        }
    }

    private static String abbreviate(String text) {
        return text.length() <= 300 ? text : text.substring(0, 300) + "...";
    }
}
