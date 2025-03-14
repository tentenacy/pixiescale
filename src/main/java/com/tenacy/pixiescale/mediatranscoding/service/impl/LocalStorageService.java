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
                    throw new RuntimeException("빈 파일은 저장할 수 없음");
                }

                String storedFilename = getStoredFilename(filename);
                Path destinationFile = getDestinationPath(storedFilename);

                try (var inputStream = file.getInputStream()) {
                    Files.copy(inputStream, destinationFile, StandardCopyOption.REPLACE_EXISTING);
                }

                log.info("파일 저장 완료: {} -> {}", file.getOriginalFilename(), destinationFile);
                return storedFilename;
            } catch (IOException e) {
                throw new RuntimeException("파일 저장 실패", e);
            }
        });
    }

    @Override
    public Mono<String> store(Path filePath, String filename) {
        return Mono.fromCallable(() -> {
            try {
                if (!Files.exists(filePath)) {
                    throw new RuntimeException("파일이 존재하지 않음: " + filePath);
                }

                String storedFilename = getStoredFilename(filename);
                Path destinationFile = getDestinationPath(storedFilename);

                Files.copy(filePath, destinationFile, StandardCopyOption.REPLACE_EXISTING);

                log.info("파일 저장 완료: {} -> {}", filePath, destinationFile);
                return storedFilename;
            } catch (IOException e) {
                throw new RuntimeException("파일 저장 실패", e);
            }
        });
    }

    @Override
    public Mono<String> store(byte[] data, String filename, String contentType) {
        return Mono.fromCallable(() -> {
            try {
                if (data == null || data.length == 0) {
                    throw new RuntimeException("빈 데이터는 저장할 수 없음");
                }

                String storedFilename = getStoredFilename(filename);
                Path destinationFile = getDestinationPath(storedFilename);

                Files.write(destinationFile, data);

                log.info("데이터 저장 완료: {} -> {} ({}바이트)", filename, destinationFile, data.length);
                return storedFilename;
            } catch (IOException e) {
                throw new RuntimeException("데이터 저장 실패", e);
            }
        });
    }

    @Override
    public Mono<Resource> loadAsResource(String filename) {
        return Mono.fromCallable(() -> {
            try {
                Path file = getDestinationPath(filename);
                Resource resource = new UrlResource(file.toUri());
                if (resource.exists() || resource.isReadable()) {
                    return resource;
                } else {
                    throw new RuntimeException("파일을 읽을 수 없음: " + filename);
                }
            } catch (MalformedURLException e) {
                throw new RuntimeException("파일을 읽을 수 없음: " + filename, e);
            }
        });
    }

    @Override
    public Mono<Void> delete(String filename) {
        return Mono.fromRunnable(() -> {
            try {
                Path file = getDestinationPath(filename);
                Files.deleteIfExists(file);
                log.info("파일 삭제 완료: {}", filename);
            } catch (IOException e) {
                log.error("파일 삭제 실패: {}", filename, e);
            }
        });
    }

    private String getStoredFilename(String filename) {
        return filename != null ? filename : UUID.randomUUID().toString();
    }

    private Path getDestinationPath(String filename) throws IOException {
        Path destinationDir = Paths.get(storageConfig.getBaseDir());
        if (!Files.exists(destinationDir)) {
            Files.createDirectories(destinationDir);
        }

        return destinationDir.resolve(Paths.get(filename)).normalize().toAbsolutePath();
    }
}