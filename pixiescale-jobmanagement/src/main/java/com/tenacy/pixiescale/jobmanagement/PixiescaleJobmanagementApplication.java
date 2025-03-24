package com.tenacy.pixiescale.jobmanagement;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties
public class PixiescaleJobmanagementApplication {

	public static void main(String[] args) {
		SpringApplication.run(PixiescaleJobmanagementApplication.class, args);
	}

}
