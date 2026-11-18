package com.swen3.ocr_worker.ocr;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Component
public class ProcessCommandRunner implements CommandRunner {

    private static final Logger log = LoggerFactory.getLogger(ProcessCommandRunner.class);
    private static final int MAX_ERROR_CHARS = 500;

    @Override
    public String run(List<String> command, Duration timeout) {
        String program = command.get(0);
        Path stdout = null;
        Path stderr = null;
        try {
            // Output goes to files, not pipes: a full pipe buffer could otherwise block the child forever
            // and make the timeout below meaningless.
            stdout = Files.createTempFile("cmd-out-", ".txt");
            stderr = Files.createTempFile("cmd-err-", ".txt");
            log.debug("Running {}", command);

            Process process = new ProcessBuilder(command)
                    .redirectOutput(stdout.toFile())
                    .redirectError(stderr.toFile())
                    .start();
            process.getOutputStream().close();   // nothing to send; never leave the child waiting on stdin

            if (!process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
                process.destroyForcibly();
                throw new OcrException(program + " did not finish within " + timeout.toSeconds() + "s");
            }
            if (process.exitValue() != 0) {
                throw new OcrException(program + " exited with code " + process.exitValue() + ": " + tail(stderr));
            }
            return new String(Files.readAllBytes(stdout), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new OcrException("could not run " + program + " (is it installed?)", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new OcrException(program + " was interrupted", e);
        } finally {
            deleteQuietly(stdout);
            deleteQuietly(stderr);
        }
    }

    private static String tail(Path file) throws IOException {
        String text = new String(Files.readAllBytes(file), StandardCharsets.UTF_8).strip();
        return text.length() <= MAX_ERROR_CHARS ? text : "..." + text.substring(text.length() - MAX_ERROR_CHARS);
    }

    private static void deleteQuietly(Path file) {
        if (file == null) {
            return;
        }
        try {
            Files.deleteIfExists(file);
        } catch (IOException ignored) {
            // temp file
        }
    }
}
