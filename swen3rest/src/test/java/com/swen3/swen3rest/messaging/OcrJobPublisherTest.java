package com.swen3.swen3rest.messaging;

import com.swen3.swen3rest.entity.Document;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * Unit tests for OcrJobPublisher: RabbitTemplate is mocked, no broker needed.
 */
@ExtendWith(OutputCaptureExtension.class)
class OcrJobPublisherTest {

    private final RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
    private final OcrJobPublisher publisher = new OcrJobPublisher(rabbitTemplate, "ocr.queue");

    @Test
    void publishesOcrJobToTheQueueAndLogs(CapturedOutput output) {
        publisher.publish(document());

        verify(rabbitTemplate).convertAndSend("ocr.queue", new OcrJobMessage(5L, "invoice.pdf"));
        assertThat(output).contains("Published OCR job for document 5 to queue 'ocr.queue'");
    }

    @Test
    void logsAndRethrowsWhenTheBrokerIsUnreachable(CapturedOutput output) {
        AmqpConnectException brokerDown = new AmqpConnectException(new ConnectException("Connection refused"));
        doThrow(brokerDown).when(rabbitTemplate).convertAndSend(anyString(), any(Object.class));

        assertThatThrownBy(() -> publisher.publish(document())).isSameAs(brokerDown);
        assertThat(output).contains("ERROR").contains("Could not publish OCR job for document 5 to queue 'ocr.queue'");
    }

    private static Document document() {
        Document document = new Document();
        document.setId(5L);
        document.setFilename("invoice.pdf");
        return document;
    }
}
