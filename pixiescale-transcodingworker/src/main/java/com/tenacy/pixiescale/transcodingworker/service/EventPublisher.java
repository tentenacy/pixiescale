package com.tenacy.pixiescale.transcodingworker.service;

import com.tenacy.pixiescale.transcodingworker.event.TaskResultEvent;
import reactor.core.publisher.Mono;

public interface EventPublisher {
    Mono<Void> publishTaskResult(TaskResultEvent event);
}