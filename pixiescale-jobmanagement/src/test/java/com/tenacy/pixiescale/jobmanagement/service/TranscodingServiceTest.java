package com.tenacy.pixiescale.jobmanagement.service;

import com.tenacy.pixiescale.common.domain.MediaFile;
import com.tenacy.pixiescale.common.domain.MediaMetadata;
import com.tenacy.pixiescale.common.domain.TranscodingConfig;
import com.tenacy.pixiescale.common.domain.TranscodingJob;
import com.tenacy.pixiescale.jobmanagement.api.dto.TranscodingJobRequest;
import com.tenacy.pixiescale.jobmanagement.event.MediaEventListener;
import com.tenacy.pixiescale.jobmanagement.service.impl.TranscodingServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class TranscodingServiceTest {

    @Mock
    private EventPublisher eventPublisher;

    @Mock
    private MediaEventListener mediaEventListener;

    private TranscodingService transcodingService;

    @BeforeEach
    void setUp() {
        // 이벤트 발행 모킹
        lenient().when(eventPublisher.publishJobCreated(any())).thenReturn(Mono.empty());
        lenient().when(eventPublisher.publishJobUpdated(any())).thenReturn(Mono.empty());
        lenient().when(eventPublisher.publishTranscodingTask(any())).thenReturn(Mono.empty());

        // 미디어 정보 모킹
        MediaMetadata metadata = MediaMetadata.builder()
                .format("mp4")
                .width(1920)
                .height(1080)
                .codec("h264")
                .duration(120.0)
                .bitrate(5000)
                .frameRate(30.0)
                .build();

        MediaFile mediaFile = MediaFile.builder()
                .id("test-media-id")
                .fileName("test.mp4")
                .contentType("video/mp4")
                .fileSize(1024L)
                .storagePath("test-path.mp4")
                .metadata(metadata)
                .uploadedAt(LocalDateTime.now())
                .build();

        lenient().when(mediaEventListener.getMediaInfo("test-media-id")).thenReturn(mediaFile);

        // 서비스 생성
        transcodingService = new TranscodingServiceImpl(eventPublisher, mediaEventListener);
    }

    @Test
    void createJobShouldCreateTasksAndPublishEvents() {
        // Arrange
        List<TranscodingConfig.ResolutionPreset> resolutions = Arrays.asList(
                TranscodingConfig.ResolutionPreset.builder()
                        .name("720p")
                        .width(1280)
                        .height(720)
                        .bitrate(2500)
                        .build(),
                TranscodingConfig.ResolutionPreset.builder()
                        .name("480p")
                        .width(854)
                        .height(480)
                        .bitrate(1500)
                        .build()
        );

        TranscodingConfig config = TranscodingConfig.builder()
                .targetCodec("H.264")
                .targetFormat("MP4")
                .resolutions(resolutions)
                .build();

        TranscodingJobRequest request = TranscodingJobRequest.builder()
                .mediaFileId("test-media-id")
                .config(config)
                .build();

        // Act & Assert
        StepVerifier.create(transcodingService.createJob(request))
                .assertNext(job -> {
                    assertNotNull(job);
                    assertEquals("test-media-id", job.getMediaFileId());
                    assertEquals(TranscodingJob.JobStatus.PROCESSING, job.getStatus());
                    assertEquals(2, job.getTasks().size());
                    assertNotNull(job.getCreatedAt());
                    assertNotNull(job.getStartedAt());

                    // 각 작업 항목 검증
                    job.getTasks().forEach(task -> {
                        assertNotNull(task.getId());
                        assertEquals(job.getId(), task.getJobId());
                        assertEquals("MP4", task.getTargetFormat());
                    });
                })
                .verifyComplete();
    }

    @Test
    void getJobShouldReturnJob() throws Exception {
        // Arrange
        String jobId = "test-job-id";
        TranscodingJob job = TranscodingJob.builder()
                .id(jobId)
                .mediaFileId("test-media-id")
                .status(TranscodingJob.JobStatus.PROCESSING)
                .createdAt(LocalDateTime.now())
                .startedAt(LocalDateTime.now())
                .tasks(Arrays.asList())
                .build();

        // TranscodingServiceImpl의 jobStore에 직접 주입
        TranscodingServiceImpl impl = (TranscodingServiceImpl) transcodingService;
        ConcurrentHashMap<String, TranscodingJob> store = new ConcurrentHashMap<>();
        store.put(jobId, job);

        java.lang.reflect.Field field = TranscodingServiceImpl.class.getDeclaredField("jobStore");
        field.setAccessible(true);
        field.set(impl, store);

        // Act & Assert
        StepVerifier.create(transcodingService.getJob(jobId))
                .expectNext(job)
                .verifyComplete();
    }

    @Test
    void cancelJobShouldUpdateStatusAndPublishEvent() throws Exception {
        // Arrange
        String jobId = "test-job-id";
        TranscodingJob job = TranscodingJob.builder()
                .id(jobId)
                .mediaFileId("test-media-id")
                .status(TranscodingJob.JobStatus.PROCESSING)
                .createdAt(LocalDateTime.now())
                .startedAt(LocalDateTime.now())
                .tasks(Arrays.asList())
                .build();

        // TranscodingServiceImpl의 jobStore에 직접 주입
        TranscodingServiceImpl impl = (TranscodingServiceImpl) transcodingService;
        ConcurrentHashMap<String, TranscodingJob> store = new ConcurrentHashMap<>();
        store.put(jobId, job);

        java.lang.reflect.Field field = TranscodingServiceImpl.class.getDeclaredField("jobStore");
        field.setAccessible(true);
        field.set(impl, store);

        // Act & Assert
        StepVerifier.create(transcodingService.cancelJob(jobId))
                .verifyComplete();

        // 작업 상태 변경 확인
        TranscodingJob updatedJob = store.get(jobId);
        assertEquals(TranscodingJob.JobStatus.FAILED, updatedJob.getStatus());
        assertEquals("사용자에 의해 취소됨", updatedJob.getErrorMessage());
    }
}