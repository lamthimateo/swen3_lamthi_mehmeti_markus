package com.swen3.ocr_worker.ocr;

import com.swen3.ocr_worker.config.OcrProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * OCR with Ghostscript + Tesseract. Tesseract reads images, not PDFs, so the pipeline is:
 * <ol>
 *   <li>Ghostscript renders every PDF page to a PNG (one file per page, in a temp directory)</li>
 *   <li>Tesseract recognises the text of each PNG</li>
 *   <li>the page texts are joined in page order</li>
 * </ol>
 */
@Component
public class TesseractOcrEngine implements OcrEngine {

    private static final Logger log = LoggerFactory.getLogger(TesseractOcrEngine.class);
    private static final String PAGE_PATTERN = "page-%04d.png";

    private final CommandRunner runner;
    private final OcrProperties properties;

    public TesseractOcrEngine(CommandRunner runner, OcrProperties properties) {
        this.runner = runner;
        this.properties = properties;
    }

    @Override
    public String extractText(Path pdf) {
        Path pagesDir = null;
        try {
            pagesDir = Files.createTempDirectory("ocr-pages-");
            renderPages(pdf, pagesDir);

            List<Path> pages = listPages(pagesDir);
            if (pages.isEmpty()) {
                throw new OcrException("Ghostscript produced no pages, is this a valid PDF?");
            }
            log.debug("Recognising {} page(s) with languages '{}'", pages.size(), properties.languages());

            List<String> pageTexts = new ArrayList<>();
            for (Path page : pages) {
                String text = recognise(page);
                if (!text.isBlank()) {
                    pageTexts.add(text);
                }
            }
            return String.join("\n\n", pageTexts);
        } catch (IOException e) {
            throw new OcrException("could not prepare temporary files for OCR", e);
        } finally {
            deleteRecursively(pagesDir);
        }
    }

    private void renderPages(Path pdf, Path pagesDir) {
        runner.run(List.of(
                properties.ghostscriptCommand(),
                "-q",                          // no banner / progress output
                "-dNOPAUSE", "-dBATCH",        // never wait for input, exit when done
                "-dSAFER",                     // the PDF may not run file-system commands
                "-sDEVICE=png16m",             // 24-bit colour PNG
                "-r" + properties.dpi(),
                "-sOutputFile=" + pagesDir.resolve(PAGE_PATTERN),
                pdf.toString()), timeout());
    }

    private String recognise(Path page) {
        String raw = runner.run(List.of(
                properties.tesseractCommand(),
                page.toString(),
                "stdout",                      // write the text to standard output, not to a file
                "-l", properties.languages()), timeout());
        return raw.replace("\f", "").strip();  // Tesseract ends every page with a form feed
    }

    private Duration timeout() {
        return Duration.ofSeconds(properties.timeoutSeconds());
    }

    private static List<Path> listPages(Path dir) throws IOException {
        try (Stream<Path> files = Files.list(dir)) {
            return files.filter(p -> p.getFileName().toString().endsWith(".png"))
                    .sorted()                  // page-0001, page-0002, ... sorts in page order
                    .toList();
        }
    }

    private static void deleteRecursively(Path dir) {
        if (dir == null) {
            return;
        }
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException ignored) {
                    // temp file
                }
            });
        } catch (IOException ignored) {
            // temp directory
        }
    }
}
