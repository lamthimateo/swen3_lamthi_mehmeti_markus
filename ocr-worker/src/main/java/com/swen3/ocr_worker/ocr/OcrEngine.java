package com.swen3.ocr_worker.ocr;

import java.nio.file.Path;

/** Turns a PDF file into plain text. */
public interface OcrEngine {

    /** @throws OcrException if the text could not be extracted */
    String extractText(Path pdf);
}
