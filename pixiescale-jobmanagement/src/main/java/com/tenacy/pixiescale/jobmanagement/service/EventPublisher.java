package com.tenacy.pixiescale.jobmanagement.service;

import com.tenacy.pixiescale.jobmanagement.event.TranscodingJobEvent;
import com.tenacy.pixiescale.jobmanagement.event.TranscodingTaskEvent;
import reactor.core.publisher.Mono;

public interface EventPublisher {
    Mono<Void> publishJobCreated(TranscodingJobEvent event);
    Mono<Void> publishJobUpdated(TranscodingJobEvent event);
    Mono<Void> publishTranscodingTask(TranscodingTaskEvent event);
}