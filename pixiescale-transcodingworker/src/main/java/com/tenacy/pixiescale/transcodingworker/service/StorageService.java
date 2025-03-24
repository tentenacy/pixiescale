package com.tenacy.pixiescale.transcodingworker.service;

import reactor.core.publisher.Mono;

import java.nio.file.Path;

public interface StorageService {
    Mono<String> store(Path filePath, String filename);
}