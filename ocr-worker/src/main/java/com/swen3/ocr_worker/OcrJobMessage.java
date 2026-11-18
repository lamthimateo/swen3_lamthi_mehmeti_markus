package com.swen3.ocr_worker;

/**
 * Payload published by swen3rest when a document has been uploaded.
 *
 * @param storagePath optional MinIO object key; when the publisher does not send it, the worker
 *                    uses the convention {@code <documentId>/<filename>} (see {@link #objectKey()})
 */
public record OcrJobMessage(Long documentId, String filename, String storagePath) {

    /** Key of the original file inside the MinIO bucket. */
    public String objectKey() {
        if (storagePath != null && !storagePath.isBlank()) {
            return storagePath;
        }
        return documentId + "/" + filename;
    }
}
