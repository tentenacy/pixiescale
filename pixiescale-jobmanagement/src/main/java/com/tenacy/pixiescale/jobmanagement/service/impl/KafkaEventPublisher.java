package com.tenacy.pixiescale.jobmanagement.service.impl;

import com.tenacy.pixiescale.common.event.TranscodingJobEvent;
import com.tenacy.pixiescale.common.event.TranscodingTaskEvent;
import com.tenacy.pixiescale.jobmanagement.service.EventPublisher;
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

    @Value("${app.kafka.topics.job-created}")
    private String jobCreatedTopic;

    @Value("${app.kafka.topics.job-updated}")
    private String jobUpdatedTopic;

    @Value("${app.kafka.topics.transcoding-task}")
    private String transcodingTaskTopic;

    @Override
    public Mono<Void> publishJobCreated(TranscodingJobEvent event) {
        return Mono.fromRunnable(() -> {
            CompletableFuture<SendResult<String, Object>> future =
                    kafkaTemplate.send(jobCreatedTopic, UUID.randomUUID().toString(), event);

            future.whenComplete((result, ex) -> {
                if (ex == null) {
                    log.info("Job created event published: {}", event.getJobId());
                } else {
                    log.error("Failed to publish job created event: {}", event.getJobId(), ex);
                }
            });
        });
    }

    @Override
    public Mono<Void> publishJobUpdated(TranscodingJobEvent event) {
        return Mono.fromRunnable(() -> {
            CompletableFuture<SendResult<String, Object>> future =
                    kafkaTemplate.send(jobUpdatedTopic, UUID.randomUUID().toString(), event);

            future.whenComplete((result, ex) -> {
                if (ex == null) {
                    log.info("Job updated event published: {}", event.getJobId());
                } else {
                    log.error("Failed to publish job updated event: {}", event.getJobId(), ex);
                }
            });
        });
    }

    @Override
    public Mono<Void> publishTranscodingTask(TranscodingTaskEvent event) {
        return Mono.fromRunnable(() -> {
            CompletableFuture<SendResult<String, Object>> future =
                    kafkaTemplate.send(transcodingTaskTopic, UUID.randomUUID().toString(), event);

            future.whenComplete((result, ex) -> {
                if (ex == null) {
                    log.info("Transcoding task event published: {}", event.getTaskId());
                } else {
                    log.error("Failed to publish transcoding task event: {}", event.getTaskId(), ex);
                }
            });
        });
    }
}