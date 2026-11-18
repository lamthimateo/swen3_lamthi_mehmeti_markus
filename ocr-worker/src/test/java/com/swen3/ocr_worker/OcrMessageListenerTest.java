package com.swen3.ocr_worker;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.swen3.ocr_worker.ocr.OcrEngine;
import com.swen3.ocr_worker.ocr.OcrException;
import com.swen3.ocr_worker.storage.DocumentNotFoundException;
import com.swen3.ocr_worker.storage.DocumentStorage;
import com.swen3.ocr_worker.storage.StorageException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OcrMessageListenerTest {

    @Mock
    private DocumentStorage storage;
    @Mock
    private OcrEngine ocrEngine;
    @Mock
    private IndexJobPublisher publisher;

    @TempDir
    private Path tempDir;

    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();
    private final Logger logger = (Logger) LoggerFactory.getLogger(OcrMessageListener.class);

    private OcrMessageListener listener;

    @BeforeEach
    void setUp() {
        listener = new OcrMessageListener(storage, ocrEngine, publisher);
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void detachAppender() {
        logger.detachAppender(appender);
    }

    @Test
    void onMessage_downloadsPdf_runsOcr_andPublishesIndexJob() throws IOException {
        Path pdf = Files.createFile(tempDir.resolve("5.pdf"));
        when(storage.download("5/scan.pdf")).thenReturn(pdf);
        when(ocrEngine.extractText(pdf)).thenReturn("Hello World");

        listener.onMessage(new OcrJobMessage(5L, "scan.pdf", null));

        verify(publisher).publish(new IndexJobMessage(5L, "scan.pdf", "Hello World"));
        assertThat(pdf).as("temporary download is cleaned up").doesNotExist();
        assertThat(appender.list)
                .extracting(ILoggingEvent::getFormattedMessage)
                .anyMatch(text -> text.contains("documentId=5") && text.contains("scan.pdf"));
    }

    @Test
    void onMessage_usesStoragePathFromMessage_whenPresent() throws IOException {
        Path pdf = Files.createFile(tempDir.resolve("a.pdf"));
        when(storage.download("custom/key.pdf")).thenReturn(pdf);
        when(ocrEngine.extractText(pdf)).thenReturn("text");

        listener.onMessage(new OcrJobMessage(7L, "scan.pdf", "custom/key.pdf"));

        verify(publisher).publish(new IndexJobMessage(7L, "scan.pdf", "text"));
    }

    @Test
    void onMessage_documentMissingInStorage_rejectsWithoutRequeue() {
        when(storage.download("5/scan.pdf")).thenThrow(new DocumentNotFoundException("no such object"));

        assertThatThrownBy(() -> listener.onMessage(new OcrJobMessage(5L, "scan.pdf", null)))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class)
                .hasCauseInstanceOf(DocumentNotFoundException.class);
        verifyNoInteractions(ocrEngine, publisher);
    }

    @Test
    void onMessage_ocrFails_rejectsWithoutRequeue_andStillDeletesDownload() throws IOException {
        Path pdf = Files.createFile(tempDir.resolve("broken.pdf"));
        when(storage.download("5/scan.pdf")).thenReturn(pdf);
        when(ocrEngine.extractText(pdf)).thenThrow(new OcrException("not a PDF"));

        assertThatThrownBy(() -> listener.onMessage(new OcrJobMessage(5L, "scan.pdf", null)))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class)
                .hasCauseInstanceOf(OcrException.class);
        assertThat(pdf).doesNotExist();
        verifyNoInteractions(publisher);
    }

    @Test
    void onMessage_storageUnavailable_propagatesSoTheMessageIsRetried() {
        when(storage.download("5/scan.pdf")).thenThrow(new StorageException("MinIO down", new IOException()));

        assertThatThrownBy(() -> listener.onMessage(new OcrJobMessage(5L, "scan.pdf", null)))
                .isInstanceOf(StorageException.class);
        verifyNoInteractions(ocrEngine, publisher);
    }

    @Test
    void onMessage_withoutDocumentId_isRejected() {
        assertThatThrownBy(() -> listener.onMessage(new OcrJobMessage(null, "scan.pdf", null)))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class);
        verifyNoInteractions(storage, ocrEngine, publisher);
    }
}
