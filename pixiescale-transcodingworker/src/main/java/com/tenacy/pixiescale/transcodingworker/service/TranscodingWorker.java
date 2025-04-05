package com.tenacy.pixiescale.transcodingworker.service;

import com.tenacy.pixiescale.common.domain.TranscodingTask;
import reactor.core.publisher.Mono;

public interface TranscodingWorker {
    Mono<TranscodingTask> processTask(TranscodingTask task);
    void initialize();
    void shutdown();
}