package com.tenacy.pixiescale.transcodingworker.config;

import com.tenacy.pixiescale.transcodingworker.service.EventPublisher;
import com.tenacy.pixiescale.transcodingworker.service.StorageService;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import reactor.core.publisher.Mono;

import java.nio.file.Path;

@TestConfiguration
public class TestConfig {

    @Bean
    @Primary
    public EventPublisher mockEventPublisher() {
        return event -> Mono.empty();
    }

    @Bean
    @Primary
    public StorageService mockStorageService() {
        return new StorageService() {
            @Override
            public Mono<String> store(Path filePath, String filename) {
                return Mono.just("stored-" + filename);
            }
        };
    }
}