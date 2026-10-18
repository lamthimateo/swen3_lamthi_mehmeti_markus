package com.swen3.ocr_worker;

import org.springframework.amqp.core.Queue;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Declares the OCR queue. Declaring it here as well as in swen3rest is intentional:
 * RabbitMQ declarations are idempotent, so whichever service starts first creates it.
 */
@Configuration
public class RabbitConfig {

    @Bean
    public Queue ocrQueue(@Value("${app.rabbitmq.ocr-queue}") String name) {
        return new Queue(name, true);   // durable
    }

    /** Reads message payloads as JSON; must match the converter used by the publisher. */
    @Bean
    public JacksonJsonMessageConverter messageConverter() {
        return new JacksonJsonMessageConverter();
    }
} 
