package com.tenacy.pixiescale.mediastorage;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties
public class PixiescaleMediastorageApplication {

	public static void main(String[] args) {
		SpringApplication.run(PixiescaleMediastorageApplication.class, args);
	}

}
