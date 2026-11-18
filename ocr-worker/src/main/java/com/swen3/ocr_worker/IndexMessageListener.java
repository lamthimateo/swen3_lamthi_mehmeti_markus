package com.swen3.ocr_worker;

import com.swen3.ocr_worker.search.DocumentIndexer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/** Indexing step: stores the OCR result in Elasticsearch so the document becomes searchable. */
@Component
public class IndexMessageListener {

    private static final Logger log = LoggerFactory.getLogger(IndexMessageListener.class);

    private final DocumentIndexer indexer;

    public IndexMessageListener(DocumentIndexer indexer) {
        this.indexer = indexer;
    }

    @RabbitListener(queues = "${app.rabbitmq.index-queue}")
    public void onMessage(IndexJobMessage message) {
        log.info("Received index job: documentId={}, filename={}", message.documentId(), message.filename());
        indexer.index(message);   // throws if Elasticsearch is unreachable; the message is then retried
        log.info("Indexed document {}", message.documentId());
    }
}
