package com.swen3.ocr_worker;

import com.swen3.ocr_worker.search.DocumentIndexer;
import com.swen3.ocr_worker.search.IndexingException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class IndexMessageListenerTest {

    @Mock
    private DocumentIndexer indexer;

    @Test
    void onMessage_passesTheDocumentToTheIndexer() {
        IndexJobMessage message = new IndexJobMessage(5L, "hello.pdf", "Hello World");

        new IndexMessageListener(indexer).onMessage(message);

        verify(indexer).index(message);
    }

    @Test
    void onMessage_indexerFails_propagatesSoTheMessageIsRetried() {
        IndexJobMessage message = new IndexJobMessage(5L, "hello.pdf", "Hello World");
        doThrow(new IndexingException("Elasticsearch down")).when(indexer).index(message);

        assertThatThrownBy(() -> new IndexMessageListener(indexer).onMessage(message))
                .isInstanceOf(IndexingException.class);
    }
}
