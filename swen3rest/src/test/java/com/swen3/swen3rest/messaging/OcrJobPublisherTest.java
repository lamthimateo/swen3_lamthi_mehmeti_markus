package com.swen3.swen3rest.messaging;

import com.swen3.swen3rest.entity.Document;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
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
}
