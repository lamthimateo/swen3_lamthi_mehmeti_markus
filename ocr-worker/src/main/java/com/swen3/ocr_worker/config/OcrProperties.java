package com.swen3.ocr_worker.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Settings for the OCR pipeline, bound from {@code app.ocr.*}.
 *
 * @param languages        Tesseract language packs joined with '+', e.g. {@code eng+deu}
 * @param dpi              resolution Ghostscript renders each page at (higher = better OCR, slower)
 * @param timeoutSeconds   upper limit for a single external command (one Ghostscript or Tesseract call)
 * @param ghostscriptCommand name or path of the Ghostscript executable
 * @param tesseractCommand   name or path of the Tesseract executable
 */
@ConfigurationProperties(prefix = "app.ocr")
public record OcrProperties(String languages, int dpi, int timeoutSeconds,
                            String ghostscriptCommand, String tesseractCommand) {
}
