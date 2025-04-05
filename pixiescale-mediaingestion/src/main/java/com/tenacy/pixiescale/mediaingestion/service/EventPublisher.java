package com.tenacy.pixiescale.mediaingestion.service;

import com.tenacy.pixiescale.common.event.MediaUploadedEvent;
import reactor.core.publisher.Mono;

public interface EventPublisher {
    Mono<Void> publishMediaUploaded(MediaUploadedEvent event);
}