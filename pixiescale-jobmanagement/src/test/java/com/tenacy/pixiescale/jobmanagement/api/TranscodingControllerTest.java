package com.tenacy.pixiescale.jobmanagement.api;

import com.tenacy.pixiescale.jobmanagement.api.dto.TranscodingJobRequest;
import com.tenacy.pixiescale.jobmanagement.api.dto.TranscodingJobResponse;
import com.tenacy.pixiescale.jobmanagement.domain.TranscodingConfig;
import com.tenacy.pixiescale.jobmanagement.domain.TranscodingJob;
import com.tenacy.pixiescale.jobmanagement.domain.TranscodingTask;
import com.tenacy.pixiescale.jobmanagement.service.TranscodingService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class TranscodingControllerTest {

    @Mock
    private TranscodingService transcodingService;

    @InjectMocks
    private TranscodingController transcodingController;

    @Test
    void createJobShouldReturnCreatedJob() {
        // Arrange
        WebTestClient webTestClient = WebTestClient.bindToController(transcodingController).build();

        TranscodingJobRequest request = TranscodingJobRequest.builder()
                .mediaFileId("test-media-id")
                .config(TranscodingConfig.builder()
                        .targetFormat("MP4")
                        .targetCodec("H.264")
                        .resolutions(Arrays.asList(
                                TranscodingConfig.ResolutionPreset.builder()
                                        .name("720p")
                                        .width(1280)
                                        .height(720)
                                        .bitrate(2500)
                                        .build()
                        ))
                        .build())
                .build();

        TranscodingJob createdJob = TranscodingJob.builder()
                .id("test-job-id")
                .mediaFileId("test-media-id")
                .status(TranscodingJob.JobStatus.PENDING)
                .createdAt(LocalDateTime.now())
                .tasks(Collections.emptyList())
                .build();

        when(transcodingService.createJob(any(TranscodingJobRequest.class))).thenReturn(Mono.just(createdJob));

        // Act & Assert
        webTestClient.post()
                .uri("/api/v1/transcoding/jobs")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.id").isEqualTo("test-job-id")
                .jsonPath("$.mediaFileId").isEqualTo("test-media-id")
                .jsonPath("$.status").isEqualTo("PENDING");
    }

    @Test
    void getJobShouldReturnJob() {
        // Arrange
        WebTestClient webTestClient = WebTestClient.bindToController(transcodingController).build();

        String jobId = "test-job-id";
        TranscodingJob job = TranscodingJob.builder()
                .id(jobId)
                .mediaFileId("test-media-id")
                .status(TranscodingJob.JobStatus.PROCESSING)
                .createdAt(LocalDateTime.now())
                .startedAt(LocalDateTime.now())
                .tasks(Collections.emptyList())
                .build();

        when(transcodingService.getJob(jobId)).thenReturn(Mono.just(job));

        // Act & Assert
        webTestClient.get()
                .uri("/api/v1/transcoding/jobs/{jobId}", jobId)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.id").isEqualTo(jobId)
                .jsonPath("$.mediaFileId").isEqualTo("test-media-id")
                .jsonPath("$.status").isEqualTo("PROCESSING");
    }

    @Test
    void getJobsByMediaIdShouldReturnJobs() {
        // Arrange
        WebTestClient webTestClient = WebTestClient.bindToController(transcodingController).build();

        String mediaId = "test-media-id";
        TranscodingJob job1 = TranscodingJob.builder()
                .id("job-1")
                .mediaFileId(mediaId)
                .status(TranscodingJob.JobStatus.COMPLETED)
                .createdAt(LocalDateTime.now().minusHours(1))
                .startedAt(LocalDateTime.now().minusHours(1))
                .completedAt(LocalDateTime.now().minusMinutes(30))
                .tasks(Collections.emptyList())
                .build();

        TranscodingJob job2 = TranscodingJob.builder()
                .id("job-2")
                .mediaFileId(mediaId)
                .status(TranscodingJob.JobStatus.PROCESSING)
                .createdAt(LocalDateTime.now())
                .startedAt(LocalDateTime.now())
                .tasks(Collections.emptyList())
                .build();

        when(transcodingService.getJobsByMediaId(mediaId)).thenReturn(Flux.just(job1, job2));

        // Act & Assert
        webTestClient.get()
                .uri("/api/v1/transcoding/jobs/media/{mediaId}", mediaId)
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(TranscodingJobResponse.class)
                .hasSize(2);
    }

    @Test
    void cancelJobShouldReturnNoContent() {
        // Arrange
        WebTestClient webTestClient = WebTestClient.bindToController(transcodingController).build();

        String jobId = "test-job-id";
        when(transcodingService.cancelJob(jobId)).thenReturn(Mono.empty());

        // Act & Assert
        webTestClient.delete()
                .uri("/api/v1/transcoding/jobs/{jobId}", jobId)
                .exchange()
                .expectStatus().isNoContent();
    }
}