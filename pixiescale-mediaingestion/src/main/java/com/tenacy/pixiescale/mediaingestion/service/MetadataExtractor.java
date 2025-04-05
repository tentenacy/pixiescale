package com.tenacy.pixiescale.mediaingestion.service;

import com.tenacy.pixiescale.common.domain.MediaMetadata;
import reactor.core.publisher.Mono;

import java.nio.file.Path;

public interface MetadataExtractor {
    Mono<MediaMetadata> extractMetadata(Path filePath);
}