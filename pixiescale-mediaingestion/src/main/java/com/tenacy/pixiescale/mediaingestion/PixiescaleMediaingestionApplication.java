package com.tenacy.pixiescale.mediaingestion;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties
public class PixiescaleMediaingestionApplication {

	public static void main(String[] args) {
		SpringApplication.run(PixiescaleMediaingestionApplication.class, args);
	}

}
