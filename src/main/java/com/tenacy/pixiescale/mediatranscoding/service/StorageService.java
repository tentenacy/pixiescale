package com.tenacy.pixiescale.mediatranscoding.service;

import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;
import reactor.core.publisher.Mono;

public interface StorageService {
    Mono<String> store(MultipartFile file, String filename);
    Mono<Resource> loadAsResource(String filename);
    Mono<Void> delete(String filename);
}