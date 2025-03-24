package com.tenacy.pixiescale.mediastorage.service;

import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;
import reactor.core.publisher.Mono;

import java.nio.file.Path;

public interface StorageService {
    Mono<String> store(MultipartFile file, String filename);
    Mono<String> store(Path filePath, String filename);
    Mono<String> store(byte[] data, String filename, String contentType);
    Mono<Resource> loadAsResource(String filename);
    Mono<Void> delete(String filename);
}