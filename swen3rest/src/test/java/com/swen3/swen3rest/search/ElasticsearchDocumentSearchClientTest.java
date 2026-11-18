package com.swen3.swen3rest.search;

import com.sun.net.httpserver.HttpServer;
import com.swen3.swen3rest.config.ElasticsearchProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Runs against a tiny local HTTP server that plays Elasticsearch, so no real cluster is needed. */
class ElasticsearchDocumentSearchClientTest {

    private HttpServer server;
    private final AtomicReference<String> method = new AtomicReference<>();
    private final AtomicReference<String> path = new AtomicReference<>();
    private final AtomicReference<String> body = new AtomicReference<>();
    private volatile int status = 200;
    private volatile String response = "{\"hits\":{\"hits\":[]}}";

    @BeforeEach
    void startFakeElasticsearch() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            method.set(exchange.getRequestMethod());
            path.set(exchange.getRequestURI().getPath());
            body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void stopFakeElasticsearch() {
        server.stop(0);
    }

    private ElasticsearchDocumentSearchClient client() {
        return new ElasticsearchDocumentSearchClient(
                new ElasticsearchProperties("http://127.0.0.1:" + server.getAddress().getPort(), "documents"));
    }

    @Test
    void findDocumentIds_returnsTheIdsInHitOrder() {
        response = "{\"hits\":{\"total\":{\"value\":2},\"hits\":[{\"_id\":\"7\"},{\"_id\":\"3\"}]}}";

        assertThat(client().findDocumentIds("hello", 50)).containsExactly(7L, 3L);
    }

    @Test
    void findDocumentIds_sendsAMultiMatchQueryOverContentAndFilename() {
        client().findDocumentIds("hello", 50);

        assertThat(method.get()).isEqualTo("POST");
        assertThat(path.get()).isEqualTo("/documents/_search");
        assertThat(body.get())
                .contains("\"multi_match\"")
                .contains("\"query\":\"hello\"")
                .contains("\"content\"")
                .contains("\"filename\"")
                .contains("\"size\":50");
    }

    @Test
    void findDocumentIds_putsTheTermIntoTheBodyAsAJsonString_notAsQuerySyntax() {
        client().findDocumentIds("\"}, \"size\": 9999, \"x\": {\"", 50);

        // the quotes of the search term are escaped, so it cannot add or change fields of the query
        assertThat(body.get()).contains("\"query\":\"\\\"}, \\\"size\\\": 9999");
        assertThat(body.get()).contains("\"size\":50");
    }

    @Test
    void findDocumentIds_noMatches_returnsEmptyList() {
        assertThat(client().findDocumentIds("nothing", 50)).isEmpty();
    }

    @Test
    void findDocumentIds_indexDoesNotExistYet_returnsEmptyList() {
        status = 404;
        response = "{\"error\":{\"type\":\"index_not_found_exception\"}}";

        assertThat(client().findDocumentIds("hello", 50)).isEmpty();
    }

    @Test
    void findDocumentIds_elasticsearchFails_throwsSearchUnavailable() {
        status = 500;

        assertThatThrownBy(() -> client().findDocumentIds("hello", 50))
                .isInstanceOf(SearchUnavailableException.class)
                .hasMessageContaining("500");
    }

    @Test
    void findDocumentIds_elasticsearchUnreachable_throwsSearchUnavailable() {
        ElasticsearchDocumentSearchClient client = client();
        server.stop(0);

        assertThatThrownBy(() -> client.findDocumentIds("hello", 50))
                .isInstanceOf(SearchUnavailableException.class)
                .hasMessageContaining("could not reach Elasticsearch");
    }
}
