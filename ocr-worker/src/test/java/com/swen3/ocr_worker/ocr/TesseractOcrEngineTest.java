package com.swen3.ocr_worker.ocr;

import com.swen3.ocr_worker.config.OcrProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Uses a fake CommandRunner, so neither Ghostscript nor Tesseract has to be installed to run these. */
class TesseractOcrEngineTest {

    private static final OcrProperties PROPERTIES = new OcrProperties("eng+deu", 300, 120, "gs", "tesseract");

    @TempDir
    private Path tempDir;

    private final List<List<String>> commands = new ArrayList<>();
    private final List<Path> pagesDirs = new ArrayList<>();

    /** Pretends to be Ghostscript (creates {@code pages} PNG files) and Tesseract (returns text named after the page). */
    private CommandRunner fakeTools(int pages) {
        return (command, timeout) -> {
            commands.add(command);
            if (command.get(0).equals("gs")) {
                String pattern = command.stream()
                        .filter(arg -> arg.startsWith("-sOutputFile="))
                        .findFirst().orElseThrow()
                        .substring("-sOutputFile=".length());
                pagesDirs.add(Path.of(pattern).getParent());
                try {
                    for (int page = 1; page <= pages; page++) {
                        Files.createFile(Path.of(String.format(pattern, page)));
                    }
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
                return "";
            }
            return "text of " + Path.of(command.get(1)).getFileName() + "\n\f";   // Tesseract ends pages with \f
        };
    }

    private Path pdf() throws IOException {
        return Files.createFile(tempDir.resolve("input.pdf"));
    }

    @Test
    void extractText_joinsPageTextsInPageOrder() throws IOException {
        String text = new TesseractOcrEngine(fakeTools(3), PROPERTIES).extractText(pdf());

        assertThat(text).isEqualTo("text of page-0001.png\n\ntext of page-0002.png\n\ntext of page-0003.png");
    }

    @Test
    void extractText_rendersWithConfiguredResolution_andRecognisesWithConfiguredLanguages() throws IOException {
        Path pdf = pdf();

        new TesseractOcrEngine(fakeTools(1), PROPERTIES).extractText(pdf);

        List<String> ghostscript = commands.get(0);
        assertThat(ghostscript).contains("-r300", "-dSAFER", "-sDEVICE=png16m").endsWith(pdf.toString());
        List<String> tesseract = commands.get(1);
        assertThat(tesseract).containsSubsequence("stdout", "-l", "eng+deu");
    }

    @Test
    void extractText_skipsPagesWithoutText() throws IOException {
        CommandRunner runner = (command, timeout) -> {
            if (command.get(0).equals("gs")) {
                return fakeTools(2).run(command, timeout);
            }
            return command.get(1).endsWith("page-0001.png") ? "\f" : "second page\n\f";
        };

        String text = new TesseractOcrEngine(runner, PROPERTIES).extractText(pdf());

        assertThat(text).isEqualTo("second page");
    }

    @Test
    void extractText_deletesTemporaryPageImages() throws IOException {
        new TesseractOcrEngine(fakeTools(2), PROPERTIES).extractText(pdf());

        assertThat(pagesDirs).hasSize(1);
        assertThat(pagesDirs.get(0)).doesNotExist();
    }

    @Test
    void extractText_noPagesRendered_throwsOcrException() throws IOException {
        assertThatThrownBy(() -> new TesseractOcrEngine(fakeTools(0), PROPERTIES).extractText(pdf()))
                .isInstanceOf(OcrException.class)
                .hasMessageContaining("no pages");
        assertThat(pagesDirs.get(0)).doesNotExist();
    }

    @Test
    void extractText_toolFails_throwsOcrExceptionAndCleansUp() throws IOException {
        CommandRunner failing = (command, timeout) -> {
            if (command.get(0).equals("gs")) {
                return fakeTools(1).run(command, timeout);
            }
            throw new OcrException("tesseract exited with code 1");
        };

        assertThatThrownBy(() -> new TesseractOcrEngine(failing, PROPERTIES).extractText(pdf()))
                .isInstanceOf(OcrException.class)
                .hasMessageContaining("tesseract");
        assertThat(pagesDirs.get(0)).doesNotExist();
    }

    @Test
    void extractText_passesTheConfiguredTimeoutToEveryCommand() throws IOException {
        List<Duration> timeouts = new ArrayList<>();
        CommandRunner recording = (command, timeout) -> {
            timeouts.add(timeout);
            return fakeTools(1).run(command, timeout);
        };

        new TesseractOcrEngine(recording, PROPERTIES).extractText(pdf());

        assertThat(timeouts).hasSize(2).containsOnly(Duration.ofSeconds(120));
    }
}
