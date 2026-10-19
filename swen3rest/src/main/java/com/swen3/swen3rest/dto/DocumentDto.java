package com.swen3.swen3rest.dto;

import java.time.LocalDateTime;

/**
 * What the API returns for a document. Mapped from the Document entity by DocumentMapper,
 * so the JPA entity itself never leaves the service layer.
 */
public record DocumentDto(
        Long id,
        String filename,
        LocalDateTime uploadDate,
        String ocrText,        // null until the OCR worker has run (Sprint 4)
        String summary,        // null until the GenAI worker has run (Sprint 5)
        String storagePath     // null until the file is stored in MinIO (Sprint 4)
) {
}
