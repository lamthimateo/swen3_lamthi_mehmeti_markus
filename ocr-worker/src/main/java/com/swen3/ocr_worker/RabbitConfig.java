package com.swen3.ocr_worker;

import org.springframework.amqp.core.Queue;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Declares the queues of this service. The OCR queue is declared here as well as in swen3rest on purpose:
 * RabbitMQ declarations are idempotent, so whichever service starts first creates it.
 */
@Configuration
public class RabbitConfig {

    /** Jobs from swen3rest: "please OCR this document". */
    @Bean
    public Queue ocrQueue(@Value("${app.rabbitmq.ocr-queue}") String name) {
        return new Queue(name, true);   // durable
    }

    /** Hand-over from the OCR step to the indexing step: "please index this text". */
    @Bean
    public Queue indexQueue(@Value("${app.rabbitmq.index-queue}") String name) {
        return new Queue(name, true);   // durable
    }

    /** Reads and writes message payloads as JSON; must match the converter used by the publisher. */
    @Bean
    public JacksonJsonMessageConverter messageConverter() {
        return new JacksonJsonMessageConverter();
    }
}
