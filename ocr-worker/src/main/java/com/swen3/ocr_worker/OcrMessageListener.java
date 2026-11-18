package com.swen3.ocr_worker;

import com.swen3.ocr_worker.ocr.OcrEngine;
import com.swen3.ocr_worker.ocr.OcrException;
import com.swen3.ocr_worker.storage.DocumentNotFoundException;
import com.swen3.ocr_worker.storage.DocumentStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * OCR step: takes a job from the OCR queue, fetches the original PDF from MinIO, extracts its text
 * and hands the text to the indexing queue.
 */
@Component
public class OcrMessageListener {

    private static final Logger log = LoggerFactory.getLogger(OcrMessageListener.class);

    private final DocumentStorage storage;
    private final OcrEngine ocrEngine;
    private final IndexJobPublisher indexJobPublisher;

    public OcrMessageListener(DocumentStorage storage, OcrEngine ocrEngine, IndexJobPublisher indexJobPublisher) {
        this.storage = storage;
        this.ocrEngine = ocrEngine;
        this.indexJobPublisher = indexJobPublisher;
    }

    @RabbitListener(queues = "${app.rabbitmq.ocr-queue}")
    public void onMessage(OcrJobMessage message) {
        log.info("Received OCR job: documentId={}, filename={}", message.documentId(), message.filename());
        if (message.documentId() == null) {
            throw new AmqpRejectAndDontRequeueException("OCR job without documentId: " + message);
        }

        Path pdf = null;
        try {
            pdf = storage.download(message.objectKey());
            String text = ocrEngine.extractText(pdf);
            log.info("OCR finished for document {}: {} characters", message.documentId(), text.length());
            indexJobPublisher.publish(new IndexJobMessage(message.documentId(), message.filename(), text));
        } catch (DocumentNotFoundException | OcrException e) {
            // permanent problem: the same message would fail again, so it must not go back on the queue
            log.error("OCR failed for document {}: {}", message.documentId(), e.getMessage());
            throw new AmqpRejectAndDontRequeueException("OCR failed for document " + message.documentId(), e);
        } finally {
            deleteQuietly(pdf);
        }
    }

    private static void deleteQuietly(Path file) {
        if (file == null) {
            return;
        }
        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            log.warn("Could not delete temporary file {}", file);
        }
    }
}
