package com.tenacy.pixiescale.mediatranscoding.service;

import com.tenacy.pixiescale.mediatranscoding.domain.MediaMetadata;
import reactor.core.publisher.Mono;

import java.nio.file.Path;

public interface MetadataExtractor {
    Mono<MediaMetadata> extractMetadata(Path filePath);
}