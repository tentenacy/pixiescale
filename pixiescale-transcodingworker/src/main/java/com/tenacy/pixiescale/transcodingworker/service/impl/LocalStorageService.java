package com.tenacy.pixiescale.transcodingworker.service.impl;

import com.tenacy.pixiescale.transcodingworker.config.StorageConfig;
import com.tenacy.pixiescale.transcodingworker.service.StorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

@Slf4j
@Service
@RequiredArgsConstructor
public class LocalStorageService implements StorageService {

    private final StorageConfig storageConfig;

    @Override
    public Mono<String> store(Path filePath, String filename) {
        return Mono.fromCallable(() -> {
            try {
                if (!Files.exists(filePath)) {
                    throw new RuntimeException("파일이 존재하지 않음: " + filePath);
                }

                Path destinationDir = Paths.get(storageConfig.getBaseDir());
                if (!Files.exists(destinationDir)) {
                    Files.createDirectories(destinationDir);
                }

                Path destinationFile = destinationDir.resolve(Paths.get(filename)).normalize().toAbsolutePath();
                Files.copy(filePath, destinationFile, StandardCopyOption.REPLACE_EXISTING);

                log.info("파일 저장 완료: {} -> {}", filePath, destinationFile);
                return filename;
            } catch (IOException e) {
                throw new RuntimeException("파일 저장 실패", e);
            }
        });
    }
}