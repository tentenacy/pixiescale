package com.tenacy.pixiescale.mediastorage.event;

import com.tenacy.pixiescale.mediastorage.service.EventPublisher;
import com.tenacy.pixiescale.mediastorage.service.StorageService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class TaskResultListenerTest {

    @Mock
    private StorageService storageService;

    @Mock
    private EventPublisher eventPublisher;

    @InjectMocks
    private TaskResultListener taskResultListener;

    @TempDir
    Path tempDir;

    @Test
    void handleTaskResultShouldStoreAndPublishSuccessEvent() throws IOException {
        // Arrange
        String taskId = "test-task-id";
        String jobId = "test-job-id";
        String outputPath = tempDir.resolve("test-output.mp4").toString();

        // 테스트 파일 생성
        Files.write(Path.of(outputPath), "test content".getBytes());

        TaskResultEvent event = new TaskResultEvent();
        event.setTaskId(taskId);
        event.setJobId(jobId);
        event.setStatus("COMPLETED");
        event.setOutputPath(outputPath);

        // 모킹 설정
        when(storageService.store(any(Path.class), anyString())).thenReturn(Mono.just("stored-path.mp4"));
        when(eventPublisher.publishStorageResult(any(StorageResultEvent.class))).thenReturn(Mono.empty());

        // Act
        taskResultListener.handleTaskResult(event);

        // Assert
        verify(storageService).store(any(Path.class), anyString());

        ArgumentCaptor<StorageResultEvent> eventCaptor = ArgumentCaptor.forClass(StorageResultEvent.class);
        verify(eventPublisher).publishStorageResult(eventCaptor.capture());

        StorageResultEvent capturedEvent = eventCaptor.getValue();
        assertEquals(taskId, capturedEvent.getTaskId());
        assertEquals(jobId, capturedEvent.getJobId());
        assertEquals("stored-path.mp4", capturedEvent.getStoragePath());
        assertEquals("video/mp4", capturedEvent.getContentType());
        assertTrue(capturedEvent.isSuccess());
    }

    @Test
    void handleTaskResultShouldPublishFailureEventWhenStorageFails() {
        // Arrange
        String taskId = "test-task-id";
        String jobId = "test-job-id";
        String outputPath = "non-existent-file.mp4"; // 존재하지 않는 파일

        TaskResultEvent event = new TaskResultEvent();
        event.setTaskId(taskId);
        event.setJobId(jobId);
        event.setStatus("COMPLETED");
        event.setOutputPath(outputPath);

        // 모킹 설정
        when(eventPublisher.publishStorageResult(any(StorageResultEvent.class))).thenReturn(Mono.empty());

        // Act
        taskResultListener.handleTaskResult(event);

        // Assert
        ArgumentCaptor<StorageResultEvent> eventCaptor = ArgumentCaptor.forClass(StorageResultEvent.class);
        verify(eventPublisher).publishStorageResult(eventCaptor.capture());

        StorageResultEvent capturedEvent = eventCaptor.getValue();
        assertEquals(taskId, capturedEvent.getTaskId());
        assertEquals(jobId, capturedEvent.getJobId());
        assertFalse(capturedEvent.isSuccess());
        assertNotNull(capturedEvent.getErrorMessage());
    }

    @Test
    void handleTaskResultShouldIgnoreNonCompletedTasks() {
        // Arrange
        TaskResultEvent event = new TaskResultEvent();
        event.setTaskId("test-task-id");
        event.setJobId("test-job-id");
        event.setStatus("FAILED");

        // Act
        taskResultListener.handleTaskResult(event);

        // Assert
        verify(storageService, never()).store(any(Path.class), anyString());
        verify(eventPublisher, never()).publishStorageResult(any(StorageResultEvent.class));
    }
}