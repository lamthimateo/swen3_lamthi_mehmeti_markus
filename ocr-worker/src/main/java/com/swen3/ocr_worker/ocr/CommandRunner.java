package com.swen3.ocr_worker.ocr;

import java.time.Duration;
import java.util.List;

/**
 * Runs an external program. A seam between the OCR logic and the operating system:
 * unit tests give the engine a fake runner, so they need neither Ghostscript nor Tesseract.
 */
@FunctionalInterface
public interface CommandRunner {

    /**
     * @return what the program wrote to standard output
     * @throws OcrException if the program cannot be started, times out or exits with a non-zero code
     */
    String run(List<String> command, Duration timeout);
}
