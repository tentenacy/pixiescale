package com.tenacy.pixiescale.mediatranscoding.service.impl;

import com.tenacy.pixiescale.mediatranscoding.domain.MediaFile;
import com.tenacy.pixiescale.mediatranscoding.service.MediaService;
import com.tenacy.pixiescale.mediatranscoding.service.MetadataExtractor;
import com.tenacy.pixiescale.mediatranscoding.service.StorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import reactor.core.publisher.Mono;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class MediaServiceImpl implements MediaService {

    private final StorageService storageService;
    private final MetadataExtractor metadataExtractor;
    private final ConcurrentHashMap<String, MediaFile> mediaFileStore = new ConcurrentHashMap<>();

    @Override
    public Mono<MediaFile> storeMedia(MultipartFile file) {
        String fileId = UUID.randomUUID().toString();
        String originalFilename = file.getOriginalFilename();
        String filename = fileId + "-" + (originalFilename != null ? originalFilename : "unknown");

        return storageService.store(file, filename)
                .flatMap(storedFilename -> {
                    Path filePath = Paths.get(storedFilename);
                    return metadataExtractor.extractMetadata(filePath)
                            .map(metadata -> {
                                MediaFile mediaFile = MediaFile.builder()
                                        .id(fileId)
                                        .fileName(originalFilename)
                                        .contentType(file.getContentType())
                                        .fileSize(file.getSize())
                                        .storagePath(storedFilename)
                                        .metadata(metadata)
                                        .uploadedAt(LocalDateTime.now())
                                        .build();

                                mediaFileStore.put(fileId, mediaFile);
                                log.info("Stored media file: {}", mediaFile);
                                return mediaFile;
                            });
                });
    }

    @Override
    public Mono<Resource> getMediaResource(String mediaId) {
        return Mono.fromCallable(() -> mediaFileStore.get(mediaId))
                .switchIfEmpty(Mono.error(new RuntimeException("Media file not found: " + mediaId)))
                .flatMap(mediaFile -> storageService.loadAsResource(mediaFile.getStoragePath()));
    }

    @Override
    public Mono<MediaFile> getMediaInfo(String mediaId) {
        return Mono.fromCallable(() -> mediaFileStore.get(mediaId))
                .switchIfEmpty(Mono.error(new RuntimeException("Media file not found: " + mediaId)));
    }

    @Override
    public Mono<Void> deleteMedia(String mediaId) {
        return Mono.fromCallable(() -> mediaFileStore.get(mediaId))
                .switchIfEmpty(Mono.error(new RuntimeException("Media file not found: " + mediaId)))
                .flatMap(mediaFile -> {
                    mediaFileStore.remove(mediaId);
                    return storageService.delete(mediaFile.getStoragePath());
                });
    }
}