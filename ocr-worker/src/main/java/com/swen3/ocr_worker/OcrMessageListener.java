package com.swen3.ocr_worker;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/** Stub OCR worker (Sprint 3): consumes messages and only logs them. Real OCR comes in Sprint 4. */
@Component
public class OcrMessageListener {

    private static final Logger log = LoggerFactory.getLogger(OcrMessageListener.class);

    @RabbitListener(queues = "${app.rabbitmq.ocr-queue}")
    public void onMessage(OcrJobMessage message) {
        log.info("Received OCR job: documentId={}, filename={}", message.documentId(), message.filename());
    }
}