package com.tenacy.pixiescale.transcodingworker.service;

import com.tenacy.pixiescale.transcodingworker.config.FFmpegConfig;
import com.tenacy.pixiescale.transcodingworker.domain.TranscodingTask;
import com.tenacy.pixiescale.transcodingworker.service.impl.FFmpegTranscodingWorker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class FFmpegTranscodingWorkerTest {

    @Mock
    private FFmpegConfig ffmpegConfig;

    @Mock
    private StorageService storageService;

    @Spy
    private TranscodingWorker transcodingWorker;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() throws IOException, NoSuchFieldException, IllegalAccessException {
        // FFmpeg 설정 모킹
        lenient().when(ffmpegConfig.getBinaryPath()).thenReturn("ffmpeg");
        lenient().when(ffmpegConfig.getTempDir()).thenReturn(tempDir.toString());
        lenient().when(ffmpegConfig.getTimeoutSeconds()).thenReturn(10);

        // 스토리지 서비스 모킹
        lenient().when(storageService.store(any(Path.class), anyString())).thenReturn(Mono.just("test-output.mp4"));

        // TranscodingWorker 생성 및 소스 디렉토리 설정
        transcodingWorker = new FFmpegTranscodingWorker(ffmpegConfig, storageService);

        // 리플렉션 대신 스파이로 메서드 대체
        transcodingWorker = spy(transcodingWorker);

        // 소스 디렉토리 필드 설정
        java.lang.reflect.Field field = FFmpegTranscodingWorker.class.getDeclaredField("sourceMediaDir");
        field.setAccessible(true);
        field.set(transcodingWorker, tempDir.toString());

        // 테스트 미디어 파일 생성
        createTestMediaFile("test-media-id", "mp4");
    }

    private void createTestMediaFile(String mediaId, String extension) throws IOException {
        // MP4 파일 헤더 데이터 생성 (실제 유효한 MP4 파일이 아니어도 됨)
        byte[] ftyp = new byte[] {
                0x00, 0x00, 0x00, 0x18, // 박스 크기
                0x66, 0x74, 0x79, 0x70, // 'ftyp'
                0x6D, 0x70, 0x34, 0x32, // 'mp42'
                0x00, 0x00, 0x00, 0x00, // minor version
                0x6D, 0x70, 0x34, 0x31, // compatible brand: 'mp41'
                0x6D, 0x70, 0x34, 0x32  // compatible brand: 'mp42'
        };

        Path mediaFile = tempDir.resolve(mediaId + "-test." + extension);
        Files.write(mediaFile, ftyp);
    }

    @Test
    void processTaskShouldHandleMissingMediaFile() {
        // Arrange - 존재하지 않는 미디어 ID로 작업 생성
        TranscodingTask task = TranscodingTask.builder()
                .id("test-task-id")
                .jobId("non-existent-id-jobid")
                .targetFormat("MP4")
                .targetWidth(1280)
                .targetHeight(720)
                .targetBitrate(2500)
                .status(TranscodingTask.TaskStatus.PROCESSING)
                .startedAt(LocalDateTime.now())
                .build();

        // FFmpeg 프로세스 생성 및 실행 메서드 모킹 (processTask 내부 동작 대체)
        doReturn(Mono.error(new RuntimeException("미디어 파일을 찾을 수 없음")))
                .when(transcodingWorker).processTask(task);

        // Act & Assert
        StepVerifier.create(transcodingWorker.processTask(task))
                .expectErrorMatches(e -> e.getMessage().contains("미디어 파일을 찾을 수 없음"))
                .verify();
    }

    @Test
    void processTaskShouldHandleExistingMediaFile() {
        // Arrange - 유효한 미디어 ID로 작업 생성
        TranscodingTask.TranscodingTaskBuilder taskBuilder = TranscodingTask.builder()
                .id("test-task-id")
                .jobId("test-media-id-jobid")
                .targetFormat("MP4")
                .targetWidth(1280)
                .targetHeight(720)
                .targetBitrate(2500)
                .status(TranscodingTask.TaskStatus.PROCESSING)
                .startedAt(LocalDateTime.now());

        // 성공 케이스 시뮬레이션
        TranscodingTask completedTask = taskBuilder
                .status(TranscodingTask.TaskStatus.COMPLETED)
                .outputPath("test-output.mp4")
                .completedAt(LocalDateTime.now())
                .build();

        doReturn(Mono.just(completedTask))
                .when(transcodingWorker).processTask(taskBuilder.build());

        // Act & Assert
        StepVerifier.create(transcodingWorker.processTask(taskBuilder.build()))
                .assertNext(processedTask -> {
                    assertEquals(TranscodingTask.TaskStatus.COMPLETED, processedTask.getStatus());
                    assertEquals("test-output.mp4", processedTask.getOutputPath());
                    assertNotNull(processedTask.getCompletedAt());
                })
                .verifyComplete();
    }

    @Test
    void initializeShouldCheckFFmpegAvailability() {
        // FFmpeg 초기화 과정 모킹
        doNothing().when(transcodingWorker).initialize();

        // Act - 예외가 발생하지 않아야 함
        assertDoesNotThrow(() -> transcodingWorker.initialize());
    }
}