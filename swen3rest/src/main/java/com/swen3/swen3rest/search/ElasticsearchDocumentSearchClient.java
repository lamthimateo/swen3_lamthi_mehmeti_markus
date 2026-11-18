package com.swen3.swen3rest.search;

import com.swen3.swen3rest.config.ElasticsearchProperties;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Searches the index the OCR worker fills, through Elasticsearch's REST API ({@code POST /<index>/_search}).
 * The query term is put into the request body through the JSON mapper, never concatenated into a string,
 * so user input cannot change the structure of the query.
 */
@Component
public class ElasticsearchDocumentSearchClient implements DocumentSearchClient {

    private static final Logger log = LoggerFactory.getLogger(ElasticsearchDocumentSearchClient.class);

    private final ElasticsearchProperties properties;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final JsonMapper json = JsonMapper.builder().build();

    public ElasticsearchDocumentSearchClient(ElasticsearchProperties properties) {
        this.properties = properties;
    }

    @Override
    public List<Long> findDocumentIds(String query, int limit) {
        Map<String, Object> body = Map.of(
                "size", limit,
                "_source", false,   // only the ids are needed; the documents themselves come from the database
                "query", Map.of("multi_match", Map.of(
                        "query", query,
                        "fields", List.of("content", "filename"))));

        String base = properties.url().endsWith("/")
                ? properties.url().substring(0, properties.url().length() - 1)
                : properties.url();
        HttpRequest request = HttpRequest.newBuilder(URI.create(base + "/" + properties.index() + "/_search"))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)))
                .build();

        try {
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 404) {
                log.debug("Search index '{}' does not exist yet, nothing has been indexed", properties.index());
                return List.of();
            }
            if (response.statusCode() / 100 != 2) {
                throw new SearchUnavailableException("Elasticsearch answered " + response.statusCode());
            }
            return readIds(response.body());
        } catch (IOException e) {
            throw new SearchUnavailableException("could not reach Elasticsearch at " + properties.url(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new SearchUnavailableException("search was interrupted", e);
        }
    }

    // response shape: {"hits": {"hits": [{"_id": "7", ...}, ...]}}
    @SuppressWarnings("unchecked")
    private List<Long> readIds(String responseBody) {
        Map<String, Object> response = json.readValue(responseBody, Map.class);
        Map<String, Object> outer = (Map<String, Object>) response.get("hits");
        List<Map<String, Object>> hits = (List<Map<String, Object>>) outer.get("hits");
        List<Long> ids = new ArrayList<>();
        for (Map<String, Object> hit : hits) {
            ids.add(Long.parseLong(String.valueOf(hit.get("_id"))));
        }
        return ids;
    }
}
