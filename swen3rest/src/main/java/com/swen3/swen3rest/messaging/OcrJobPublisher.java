package com.swen3.swen3rest.messaging;

import com.swen3.swen3rest.entity.Document;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class OcrJobPublisher {

    private final RabbitTemplate rabbitTemplate;
    private final String queueName;

    public OcrJobPublisher(RabbitTemplate rabbitTemplate,
                           @Value("${app.rabbitmq.ocr-queue}") String queueName) {
        this.rabbitTemplate = rabbitTemplate;
        this.queueName = queueName;
    }

    public void publish(Document document) {
        rabbitTemplate.convertAndSend(queueName, new OcrJobMessage(document.getId(), document.getFilename()));
    }
}