package com.tenacy.pixiescale.mediastorage.integration;

import com.tenacy.pixiescale.mediastorage.event.TaskResultEvent;
import com.tenacy.pixiescale.mediastorage.service.EventPublisher;
import com.tenacy.pixiescale.mediastorage.service.StorageService;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;
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
            public Mono<String> store(MultipartFile file, String filename) {
                return Mono.just(filename);
            }

            @Override
            public Mono<String> store(Path filePath, String filename) {
                return Mono.just(filename);
            }

            @Override
            public Mono<String> store(byte[] data, String filename, String contentType) {
                return Mono.just(filename);
            }

            @Override
            public Mono<Resource> loadAsResource(String filename) {
                return Mono.just(new ByteArrayResource("test content".getBytes()) {
                    @Override
                    public String getFilename() {
                        return filename;
                    }
                });
            }

            @Override
            public Mono<Void> delete(String filename) {
                return Mono.empty();
            }
        };
    }
}