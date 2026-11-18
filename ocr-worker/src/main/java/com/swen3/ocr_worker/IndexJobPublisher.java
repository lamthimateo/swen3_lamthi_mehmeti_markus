package com.swen3.ocr_worker;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Sends the OCR result to the indexing queue. */
@Component
public class IndexJobPublisher {

    private static final Logger log = LoggerFactory.getLogger(IndexJobPublisher.class);

    private final RabbitTemplate rabbitTemplate;
    private final String queueName;

    public IndexJobPublisher(RabbitTemplate rabbitTemplate, @Value("${app.rabbitmq.index-queue}") String queueName) {
        this.rabbitTemplate = rabbitTemplate;
        this.queueName = queueName;
    }

    public void publish(IndexJobMessage message) {
        // default exchange: the routing key is the queue name
        rabbitTemplate.convertAndSend(queueName, message);
        log.info("Published index job for document {} to queue '{}'", message.documentId(), queueName);
    }
}
