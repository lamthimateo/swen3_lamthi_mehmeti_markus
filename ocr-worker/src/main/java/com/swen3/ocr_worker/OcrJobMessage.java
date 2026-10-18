package com.swen3.ocr_worker;

/** Payload published by swen3rest when a document has been uploaded. */
public record OcrJobMessage(Long documentId, String filename) {
}