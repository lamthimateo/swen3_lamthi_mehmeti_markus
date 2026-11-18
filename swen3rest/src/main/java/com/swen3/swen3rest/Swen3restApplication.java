package com.swen3.swen3rest;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan   // picks up the MinIO / Elasticsearch property records in .config
public class Swen3restApplication {

	public static void main(String[] args) {
		SpringApplication.run(Swen3restApplication.class, args);
	}

}
