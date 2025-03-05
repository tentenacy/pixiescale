package com.tenacy.pixiescale.service;

import com.tenacy.pixiescale.domain.TranscodingTask;
import reactor.core.publisher.Mono;

public interface TranscodingWorker {
    Mono<TranscodingTask> processTask(TranscodingTask task);
    void initialize();
    void shutdown();
}