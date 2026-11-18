package com.swen3.ocr_worker.search;

import com.sun.net.httpserver.HttpServer;
import com.swen3.ocr_worker.IndexJobMessage;
import com.swen3.ocr_worker.config.ElasticsearchProperties;
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
class ElasticsearchDocumentIndexerTest {

    private HttpServer server;
    private final AtomicReference<String> method = new AtomicReference<>();
    private final AtomicReference<String> path = new AtomicReference<>();
    private final AtomicReference<String> contentType = new AtomicReference<>();
    private final AtomicReference<String> body = new AtomicReference<>();
    private volatile int status = 201;

    @BeforeEach
    void startFakeElasticsearch() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            method.set(exchange.getRequestMethod());
            path.set(exchange.getRequestURI().getPath());
            contentType.set(exchange.getRequestHeaders().getFirst("Content-Type"));
            body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] response = "{\"result\":\"created\"}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void stopFakeElasticsearch() {
        server.stop(0);
    }

    private ElasticsearchDocumentIndexer indexer() {
        return new ElasticsearchDocumentIndexer(
                new ElasticsearchProperties("http://127.0.0.1:" + server.getAddress().getPort(), "documents"));
    }

    @Test
    void index_putsTheDocumentUnderItsDatabaseId() {
        indexer().index(new IndexJobMessage(5L, "hello.pdf", "Hello World"));

        assertThat(method.get()).isEqualTo("PUT");
        assertThat(path.get()).isEqualTo("/documents/_doc/5");
        assertThat(contentType.get()).isEqualTo("application/json");
        assertThat(body.get())
                .contains("\"documentId\":5")
                .contains("\"filename\":\"hello.pdf\"")
                .contains("\"content\":\"Hello World\"")
                .contains("\"indexedAt\":");
    }

    @Test
    void index_escapesQuotesAndLineBreaksInTheText() {
        indexer().index(new IndexJobMessage(5L, "a.pdf", "Say \"hi\"\nnext line"));

        assertThat(body.get()).contains("Say \\\"hi\\\"\\nnext line");
    }

    @Test
    void index_nullTextIsStoredAsEmptyContent() {
        indexer().index(new IndexJobMessage(5L, "blank.pdf", null));

        assertThat(body.get()).contains("\"content\":\"\"");
    }

    @Test
    void index_elasticsearchAnswersWithAnError_throwsIndexingException() {
        status = 500;

        assertThatThrownBy(() -> indexer().index(new IndexJobMessage(5L, "hello.pdf", "Hello")))
                .isInstanceOf(IndexingException.class)
                .hasMessageContaining("500")
                .hasMessageContaining("5");
    }

    @Test
    void index_elasticsearchUnreachable_throwsIndexingException() {
        ElasticsearchDocumentIndexer indexer = indexer();
        server.stop(0);

        assertThatThrownBy(() -> indexer.index(new IndexJobMessage(5L, "hello.pdf", "Hello")))
                .isInstanceOf(IndexingException.class)
                .hasMessageContaining("could not reach Elasticsearch");
    }
}
