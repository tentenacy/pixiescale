package com.tenacy.pixiescale.transcodingworker.service.impl;

import com.tenacy.pixiescale.common.event.TaskResultEvent;
import com.tenacy.pixiescale.transcodingworker.service.EventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
@RequiredArgsConstructor
public class KafkaEventPublisher implements EventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${app.kafka.topics.task-result}")
    private String taskResultTopic;

    @Override
    public Mono<Void> publishTaskResult(TaskResultEvent event) {
        return Mono.fromRunnable(() -> {
            CompletableFuture<SendResult<String, Object>> future =
                    kafkaTemplate.send(taskResultTopic, event.getTaskId(), event);

            future.whenComplete((result, ex) -> {
                if (ex == null) {
                    log.info("Task result event published: {}", event.getTaskId());
                } else {
                    log.error("Failed to publish task result event: {}", event.getTaskId(), ex);
                }
            });
        });
    }
}