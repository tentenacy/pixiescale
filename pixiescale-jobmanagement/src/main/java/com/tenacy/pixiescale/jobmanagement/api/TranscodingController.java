package com.tenacy.pixiescale.jobmanagement.api;

import com.tenacy.pixiescale.jobmanagement.api.dto.TranscodingJobRequest;
import com.tenacy.pixiescale.jobmanagement.api.dto.TranscodingJobResponse;
import com.tenacy.pixiescale.jobmanagement.domain.TranscodingJob;
import com.tenacy.pixiescale.jobmanagement.domain.TranscodingTask;
import com.tenacy.pixiescale.jobmanagement.service.TranscodingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Slf4j
@RestController
@RequestMapping("/api/v1/transcoding")
@RequiredArgsConstructor
public class TranscodingController {

    private final TranscodingService transcodingService;

    @PostMapping("/jobs")
    public Mono<ResponseEntity<TranscodingJobResponse>> createJob(@RequestBody TranscodingJobRequest request) {
        return transcodingService.createJob(request)
                .map(this::toResponse)
                .map(ResponseEntity::ok)
                .doOnSuccess(response -> log.info("트랜스코딩 작업 생성: mediaFileId={}", request.getMediaFileId()));
    }

    @GetMapping("/jobs/{jobId}")
    public Mono<ResponseEntity<TranscodingJobResponse>> getJob(@PathVariable String jobId) {
        return transcodingService.getJob(jobId)
                .map(this::toResponse)
                .map(ResponseEntity::ok);
    }

    @GetMapping("/jobs/media/{mediaId}")
    public Flux<TranscodingJobResponse> getJobsByMediaId(@PathVariable String mediaId) {
        return transcodingService.getJobsByMediaId(mediaId)
                .map(this::toResponse);
    }

    @DeleteMapping("/jobs/{jobId}")
    public Mono<ResponseEntity<Void>> cancelJob(@PathVariable String jobId) {
        return transcodingService.cancelJob(jobId)
                .then(Mono.just(ResponseEntity.noContent().<Void>build()))
                .doOnSuccess(response -> log.info("트랜스코딩 작업 취소: jobId={}", jobId));
    }

    private TranscodingJobResponse toResponse(TranscodingJob job) {
        return TranscodingJobResponse.builder()
                .id(job.getId())
                .mediaFileId(job.getMediaFileId())
                .status(job.getStatus())
                .totalTasks(job.getTasks().size())
                .completedTasks((int) job.getTasks().stream()
                        .filter(task -> task.getStatus() == TranscodingTask.TaskStatus.COMPLETED)
                        .count())
                .createdAt(job.getCreatedAt())
                .startedAt(job.getStartedAt())
                .completedAt(job.getCompletedAt())
                .build();
    }
}