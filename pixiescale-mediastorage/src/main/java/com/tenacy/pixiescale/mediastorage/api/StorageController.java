package com.tenacy.pixiescale.mediastorage.api;

import com.tenacy.pixiescale.mediastorage.service.StorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Slf4j
@RestController
@RequestMapping("/api/v1/storage")
@RequiredArgsConstructor
public class StorageController {

    private final StorageService storageService;

    @GetMapping("/{filename:.+}")
    public Mono<ResponseEntity<Resource>> getFile(@PathVariable String filename) {
        return storageService.loadAsResource(filename)
                .flatMap(resource -> Mono.fromCallable(() -> {
                    String contentType = determineContentType(resource);
                    return ResponseEntity.ok()
                            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + resource.getFilename() + "\"")
                            .contentType(MediaType.parseMediaType(contentType))
                            .body(resource);
                }))
                .onErrorResume(e -> {
                    log.error("파일 로드 중 오류: {}", e.getMessage());
                    return Mono.just(ResponseEntity.notFound().build());
                });
    }

    @DeleteMapping("/{filename:.+}")
    public Mono<ResponseEntity<Void>> deleteFile(@PathVariable String filename) {
        return storageService.delete(filename)
                .then(Mono.just(ResponseEntity.noContent().<Void>build()))
                .onErrorResume(e -> {
                    log.error("파일 삭제 중 오류: {}", e.getMessage());
                    return Mono.just(ResponseEntity.notFound().build());
                });
    }

    private String determineContentType(String filePath) {
        String extension = filePath.substring(filePath.lastIndexOf('.') + 1).toLowerCase();

        switch (extension) {
            case "mp4":
                return "video/mp4";
            case "webm":
                return "video/webm";
            case "mkv":
                return "video/x-matroska";
            case "avi":
                return "video/x-msvideo";
            case "mov":
                return "video/quicktime";
            case "wmv":
                return "video/x-ms-wmv";
            case "flv":
                return "video/x-flv";
            case "m4v":
                return "video/x-m4v";
            case "m3u8":
                return "application/x-mpegURL";
            case "ts":
                return "video/MP2T";
            case "3gp":
                return "video/3gpp";
            case "mpg":
            case "mpeg":
                return "video/mpeg";
            default:
                return "application/octet-stream";
        }
    }
}