package com.tenacy.pixiescale.mediatranscoding.api;

import com.tenacy.pixiescale.mediatranscoding.api.dto.MediaUploadResponse;
import com.tenacy.pixiescale.mediatranscoding.domain.MediaFile;
import com.tenacy.pixiescale.mediatranscoding.service.MediaService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/media")
@RequiredArgsConstructor
public class MediaController {

    private final MediaService mediaService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Mono<ResponseEntity<MediaUploadResponse>> uploadMedia(@RequestPart("file") Mono<FilePart> filePart) {
        return filePart
                .flatMap(part -> {
                    String tempDir = System.getProperty("java.io.tmpdir");
                    String tempFileName = UUID.randomUUID().toString();
                    Path tempPath = Path.of(tempDir, tempFileName);
                    File tempFile = tempPath.toFile();

                    return part.transferTo(tempFile)
                            .then(Mono.fromCallable(() -> {
                                try {
                                    return Files.readAllBytes(tempPath);
                                } catch (IOException e) {
                                    throw new RuntimeException("Failed to read temp file", e);
                                } finally {
                                    tempFile.delete();
                                }
                            }));
                })
                .map(bytes -> {
                    // TODO: Convert bytes to MultipartFile and use mediaService
                    // This is a simplified implementation
                    MediaFile mediaFile = MediaFile.builder()
                            .id(UUID.randomUUID().toString())
                            .fileName("temp-file.mp4")
                            .contentType("video/mp4")
                            .fileSize(bytes.length)
                            .uploadedAt(java.time.LocalDateTime.now())
                            .build();

                    return ResponseEntity.ok(new MediaUploadResponse(mediaFile.getId(), mediaFile.getFileName()));
                });
    }

    @GetMapping("/{mediaId}")
    public Mono<ResponseEntity<Resource>> getMedia(@PathVariable String mediaId) {
        return mediaService.getMediaResource(mediaId)
                .flatMap(resource -> Mono.fromCallable(() -> {
                    String contentType = determineContentType(resource);
                    return ResponseEntity.ok()
                            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + resource.getFilename() + "\"")
                            .contentType(MediaType.parseMediaType(contentType))
                            .body(resource);
                }));
    }

    @GetMapping("/{mediaId}/info")
    public Mono<ResponseEntity<MediaFile>> getMediaInfo(@PathVariable String mediaId) {
        return mediaService.getMediaInfo(mediaId)
                .map(ResponseEntity::ok);
    }

    @DeleteMapping("/{mediaId}")
    public Mono<ResponseEntity<Void>> deleteMedia(@PathVariable String mediaId) {
        return mediaService.deleteMedia(mediaId)
                .then(Mono.just(ResponseEntity.noContent().<Void>build()));
    }

    private String determineContentType(Resource resource) {
        try {
            return Files.probeContentType(Path.of(resource.getURI()));
        } catch (IOException e) {
            return MediaType.APPLICATION_OCTET_STREAM_VALUE;
        }
    }
}