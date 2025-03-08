package com.tenacy.pixiescale.mediatranscoding.service.impl;

import com.tenacy.pixiescale.mediatranscoding.config.StorageConfig;
import com.tenacy.pixiescale.mediatranscoding.service.StorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class LocalStorageService implements StorageService {

    private final StorageConfig storageConfig;

    @Override
    public Mono<String> store(MultipartFile file, String filename) {
        return Mono.fromCallable(() -> {
            try {
                if (file.isEmpty()) {
                    throw new RuntimeException("Failed to store empty file");
                }

                Path destinationDir = Paths.get(storageConfig.getBaseDir());
                if (!Files.exists(destinationDir)) {
                    Files.createDirectories(destinationDir);
                }

                String storedFilename = filename != null ? filename : UUID.randomUUID().toString();
                Path destinationFile = destinationDir.resolve(
                        Paths.get(storedFilename)).normalize().toAbsolutePath();

                try (var inputStream = file.getInputStream()) {
                    Files.copy(inputStream, destinationFile, StandardCopyOption.REPLACE_EXISTING);
                }

                log.info("Stored file: {} to {}", file.getOriginalFilename(), destinationFile);
                return storedFilename;
            } catch (IOException e) {
                throw new RuntimeException("Failed to store file", e);
            }
        });
    }

    @Override
    public Mono<Resource> loadAsResource(String filename) {
        return Mono.fromCallable(() -> {
            try {
                Path file = Paths.get(storageConfig.getBaseDir()).resolve(filename);
                Resource resource = new UrlResource(file.toUri());
                if (resource.exists() || resource.isReadable()) {
                    return resource;
                } else {
                    throw new RuntimeException("Could not read file: " + filename);
                }
            } catch (MalformedURLException e) {
                throw new RuntimeException("Could not read file: " + filename, e);
            }
        });
    }

    @Override
    public Mono<Void> delete(String filename) {
        return Mono.fromRunnable(() -> {
            try {
                Path file = Paths.get(storageConfig.getBaseDir()).resolve(filename);
                Files.deleteIfExists(file);
                log.info("Deleted file: {}", filename);
            } catch (IOException e) {
                log.error("Error deleting file: {}", filename, e);
            }
        });
    }
}