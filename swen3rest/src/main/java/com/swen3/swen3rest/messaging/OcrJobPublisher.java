package com.swen3.swen3rest.messaging;

import com.swen3.swen3rest.entity.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class OcrJobPublisher {

    private static final Logger log = LoggerFactory.getLogger(OcrJobPublisher.class);

    private final RabbitTemplate rabbitTemplate;
    private final String queueName;

    public OcrJobPublisher(RabbitTemplate rabbitTemplate,
                           @Value("${app.rabbitmq.ocr-queue}") String queueName) {
        this.rabbitTemplate = rabbitTemplate;
        this.queueName = queueName;
    }

    public void publish(Document document) {
        try {
            rabbitTemplate.convertAndSend(queueName, new OcrJobMessage(document.getId(), document.getFilename()));
        } catch (AmqpException e) {
            log.error("Could not publish OCR job for document {} to queue '{}'", document.getId(), queueName, e);
            throw e;
        }
        log.info("Published OCR job for document {} to queue '{}'", document.getId(), queueName);
    }
}