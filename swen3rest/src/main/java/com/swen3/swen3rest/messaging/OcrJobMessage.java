package com.swen3.swen3rest.messaging;

public record OcrJobMessage(Long documentId, String filename) {
    
}