package com.tenacy.pixiescale.mediatranscoding.service;

import com.tenacy.pixiescale.mediatranscoding.domain.TranscodingTask;
import reactor.core.publisher.Mono;

public interface TranscodingWorker {
    Mono<TranscodingTask> processTask(TranscodingTask task);
    void initialize();
    void shutdown();
}