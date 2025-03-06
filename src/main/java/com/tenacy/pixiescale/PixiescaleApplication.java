package com.tenacy.pixiescale;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties
public class PixiescaleApplication {

	public static void main(String[] args) {
		SpringApplication.run(PixiescaleApplication.class, args);
	}

}
