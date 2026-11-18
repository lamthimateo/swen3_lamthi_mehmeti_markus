package com.swen3.ocr_worker;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan   // picks up the MinIO / OCR / Elasticsearch property records in .config
public class OcrWorkerApplication {

	public static void main(String[] args) {
		SpringApplication.run(OcrWorkerApplication.class, args);
	}

}
