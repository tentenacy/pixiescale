package com.tenacy.pixiescale.mediaingestion.service;

import com.tenacy.pixiescale.mediaingestion.domain.MediaMetadata;
import reactor.core.publisher.Mono;

import java.nio.file.Path;

public interface MetadataExtractor {
    Mono<MediaMetadata> extractMetadata(Path filePath);
}