package com.tenacy.pixiescale.transcodingworker.service;

import com.tenacy.pixiescale.common.event.TaskResultEvent;
import reactor.core.publisher.Mono;

public interface EventPublisher {
    Mono<Void> publishTaskResult(TaskResultEvent event);
}