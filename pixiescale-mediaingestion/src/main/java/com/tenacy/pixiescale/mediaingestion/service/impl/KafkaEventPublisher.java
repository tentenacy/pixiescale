package com.tenacy.pixiescale.mediaingestion.service.impl;

import com.tenacy.pixiescale.common.event.MediaUploadedEvent;
import com.tenacy.pixiescale.mediaingestion.service.EventPublisher;
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

    @Value("${app.kafka.topics.media-uploaded}")
    private String mediaUploadedTopic;

    @Override
    public Mono<Void> publishMediaUploaded(MediaUploadedEvent event) {
        return Mono.fromRunnable(() -> {
            CompletableFuture<SendResult<String, Object>> future =
                    kafkaTemplate.send(mediaUploadedTopic, event.getMediaId(), event);

            future.whenComplete((result, ex) -> {
                if (ex == null) {
                    log.info("Media uploaded event published: {}", event.getMediaId());
                } else {
                    log.error("Failed to publish media uploaded event: {}", event.getMediaId(), ex);
                }
            });
        });
    }
}