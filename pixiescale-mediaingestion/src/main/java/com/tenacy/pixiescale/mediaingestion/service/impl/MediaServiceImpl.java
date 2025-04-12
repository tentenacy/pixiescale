package com.tenacy.pixiescale.mediaingestion.service.impl;

import com.tenacy.pixiescale.common.domain.MediaFile;
import com.tenacy.pixiescale.common.event.MediaUploadedEvent;
import com.tenacy.pixiescale.mediaingestion.config.StorageConfig;
import com.tenacy.pixiescale.mediaingestion.service.EventPublisher;
import com.tenacy.pixiescale.mediaingestion.service.MediaService;
import com.tenacy.pixiescale.mediaingestion.service.MetadataExtractor;
import com.tenacy.pixiescale.mediaingestion.service.StorageService;
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

    private final StorageConfig storageConfig;
    private final StorageService storageService;
    private final MetadataExtractor metadataExtractor;
    private final EventPublisher eventPublisher;
    private final ConcurrentHashMap<String, MediaFile> mediaFileStore = new ConcurrentHashMap<>();

    @Override
    public Mono<MediaFile> storeMedia(MultipartFile file) {
        String fileId = UUID.randomUUID().toString();
        String originalFilename = file.getOriginalFilename();
        String filename = fileId + "-" + (originalFilename != null ? originalFilename : "unknown");

        return storageService.store(file, filename)
                .flatMap(storedFilename -> {
                    Path baseDir = Paths.get(storageConfig.getBaseDir());
                    Path fullPath = baseDir.resolve(storedFilename);
                    return metadataExtractor.extractMetadata(fullPath)
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
                                log.info("미디어 파일 저장: {}", mediaFile);
                                return mediaFile;
                            });
                })
                .doOnNext(mediaFile -> {
                    // 미디어 업로드 이벤트 발행
                    MediaUploadedEvent event = MediaUploadedEvent.builder()
                            .mediaId(mediaFile.getId())
                            .fileName(mediaFile.getFileName())
                            .contentType(mediaFile.getContentType())
                            .fileSize(mediaFile.getFileSize())
                            .storagePath(mediaFile.getStoragePath())
                            .metadata(mediaFile.getMetadata())
                            .build();

                    eventPublisher.publishMediaUploaded(event)
                            .subscribe(
                                    VoidUnused -> log.debug("미디어 업로드 이벤트 발행 성공: {}", mediaFile.getId()),
                                    error -> log.error("미디어 업로드 이벤트 발행 실패: {}", mediaFile.getId(), error)
                            );
                });
    }

    @Override
    public Mono<Resource> getMediaResource(String mediaId) {
        return Mono.fromCallable(() -> mediaFileStore.get(mediaId))
                .switchIfEmpty(Mono.error(new RuntimeException("미디어 파일을 찾을 수 없음: " + mediaId)))
                .flatMap(mediaFile -> storageService.loadAsResource(mediaFile.getStoragePath()));
    }

    @Override
    public Mono<MediaFile> getMediaInfo(String mediaId) {
        return Mono.fromCallable(() -> mediaFileStore.get(mediaId))
                .switchIfEmpty(Mono.error(new RuntimeException("미디어 파일을 찾을 수 없음: " + mediaId)));
    }

    @Override
    public Mono<Void> deleteMedia(String mediaId) {
        return Mono.fromCallable(() -> mediaFileStore.get(mediaId))
                .switchIfEmpty(Mono.error(new RuntimeException("미디어 파일을 찾을 수 없음: " + mediaId)))
                .flatMap(mediaFile -> {
                    mediaFileStore.remove(mediaId);
                    return storageService.delete(mediaFile.getStoragePath());
                });
    }
}