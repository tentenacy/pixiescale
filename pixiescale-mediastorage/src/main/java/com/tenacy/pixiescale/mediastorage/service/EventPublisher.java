package com.tenacy.pixiescale.mediastorage.service;

import com.tenacy.pixiescale.common.event.StorageResultEvent;
import reactor.core.publisher.Mono;

public interface EventPublisher {
    Mono<Void> publishStorageResult(StorageResultEvent event);
}