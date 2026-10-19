package com.swen3.swen3rest.messaging;

import com.swen3.swen3rest.entity.Document;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.AmqpConnectException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import java.net.ConnectException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
class OcrJobPublisherTest {

    @Mock
    private RabbitTemplate rabbitTemplate;

    @Test
    void publish_sendsDocumentIdAndFilenameToTheConfiguredQueue() {
        OcrJobPublisher publisher = new OcrJobPublisher(rabbitTemplate, "ocr.queue");
        Document document = new Document();
        document.setId(7L);
        document.setFilename("invoice.pdf");

        publisher.publish(document);

        // routing key = queue name (default exchange); payload = id + filename only
        verify(rabbitTemplate).convertAndSend("ocr.queue", new OcrJobMessage(7L, "invoice.pdf"));
    }

    @Test
    void publish_logsAndRethrowsWhenTheBrokerIsUnreachable(CapturedOutput output) {
        OcrJobPublisher publisher = new OcrJobPublisher(rabbitTemplate, "ocr.queue");
        Document document = new Document();
        document.setId(5L);
        document.setFilename("invoice.pdf");
        AmqpConnectException brokerDown = new AmqpConnectException(new ConnectException("Connection refused"));
        doThrow(brokerDown).when(rabbitTemplate).convertAndSend(anyString(), any(Object.class));

        // rethrown so the upload transaction rolls back instead of keeping a document without an OCR job
        assertThatThrownBy(() -> publisher.publish(document)).isSameAs(brokerDown);
        assertThat(output).contains("ERROR").contains("Could not publish OCR job for document 5 to queue 'ocr.queue'");
    }
}
