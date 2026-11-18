package com.swen3.ocr_worker;

/**
 * Internal message from the OCR step to the indexing step: the recognised text of one document.
 * Only used between the two listeners of this service (queue {@code app.rabbitmq.index-queue}).
 */
public record IndexJobMessage(Long documentId, String filename, String text) {
}
