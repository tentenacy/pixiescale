package com.tenacy.pixiescale.mediastorage.service.impl;

import com.tenacy.pixiescale.common.event.StorageResultEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.test.StepVerifier;

import java.util.concurrent.CompletableFuture;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class KafkaEventPublisherTest {

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    private KafkaEventPublisher eventPublisher;

    @BeforeEach
    void setUp() {
        eventPublisher = new KafkaEventPublisher(kafkaTemplate);
        ReflectionTestUtils.setField(eventPublisher, "storageResultTopic", "storage-result-test");
    }

    @Test
    void publishStorageResultShouldPublishEvent() {
        // Arrange
        StorageResultEvent event = StorageResultEvent.builder()
                .taskId("test-task-id")
                .jobId("test-job-id")
                .storagePath("test-path.mp4")
                .contentType("video/mp4")
                .success(true)
                .build();

        SendResult<String, Object> mockSendResult = mock(SendResult.class);
        CompletableFuture<SendResult<String, Object>> future = CompletableFuture.completedFuture(mockSendResult);

        when(kafkaTemplate.send(anyString(), anyString(), any())).thenReturn(future);

        // Act & Assert
        StepVerifier.create(eventPublisher.publishStorageResult(event))
                .verifyComplete();

        verify(kafkaTemplate).send("storage-result-test", "test-task-id", event);
    }

    @Test
    void publishStorageResultShouldHandleError() {
        // Arrange
        StorageResultEvent event = StorageResultEvent.builder()
                .taskId("test-task-id")
                .jobId("test-job-id")
                .build();

        CompletableFuture<SendResult<String, Object>> future = new CompletableFuture<>();
        future.completeExceptionally(new RuntimeException("Kafka send error"));

        when(kafkaTemplate.send(anyString(), anyString(), any())).thenReturn(future);

        // Act & Assert - 예외가 전파되지 않고 로깅만 해야 함
        StepVerifier.create(eventPublisher.publishStorageResult(event))
                .verifyComplete();

        verify(kafkaTemplate).send("storage-result-test", "test-task-id", event);
    }
}