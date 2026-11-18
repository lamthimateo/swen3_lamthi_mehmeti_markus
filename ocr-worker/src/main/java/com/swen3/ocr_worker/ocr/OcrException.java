package com.swen3.ocr_worker.ocr;

/** Text extraction failed (corrupt PDF, Ghostscript/Tesseract error, timeout, tool not installed). */
public class OcrException extends RuntimeException {

    public OcrException(String message) {
        super(message);
    }

    public OcrException(String message, Throwable cause) {
        super(message, cause);
    }
}
