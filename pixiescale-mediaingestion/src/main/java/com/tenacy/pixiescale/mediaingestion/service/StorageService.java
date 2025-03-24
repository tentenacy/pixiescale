package com.tenacy.pixiescale.mediaingestion.service;

import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;
import reactor.core.publisher.Mono;

import java.nio.file.Path;

public interface StorageService {
    Mono<String> store(MultipartFile file, String filename); // 웹 요청에서 받은 파일 저장
    Mono<String> store(Path filePath, String filename); // 로컬 파일 경로에서 저장
    Mono<String> store(byte[] data, String filename, String contentType); // 바이트 배열에서 저장
    Mono<Resource> loadAsResource(String filename);
    Mono<Void> delete(String filename);
}