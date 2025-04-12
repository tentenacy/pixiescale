package com.tenacy.pixiescale.mediastorage.service.impl;

import com.tenacy.pixiescale.common.event.StorageResultEvent;
import com.tenacy.pixiescale.mediastorage.service.EventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
@RequiredArgsConstructor
public class KafkaEventPublisher implements EventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${app.kafka.topics.storage-result}")
    private String storageResultTopic;

    @Override
    public Mono<Void> publishStorageResult(StorageResultEvent event) {
        return Mono.fromRunnable(() -> {
            CompletableFuture<SendResult<String, Object>> future =
                    kafkaTemplate.send(storageResultTopic, UUID.randomUUID().toString(), event);

            future.whenComplete((result, ex) -> {
                if (ex == null) {
                    log.info("Storage result event published: {}", event.getTaskId());
                } else {
                    log.error("Failed to publish storage result event: {}", event.getTaskId(), ex);
                }
            });
        });
    }
}