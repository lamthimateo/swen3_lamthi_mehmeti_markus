package com.swen3.swen3rest.dto;

/**
 * A label attached to a document. Only the document's id is included, not the whole
 * document: the caller already knows it from the URL, and ocrText can get large.
 */
public record DocumentLabelDto(Long id, Long documentId, LabelDto label, Double weight) {
}
