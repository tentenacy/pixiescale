package com.tenacy.pixiescale.mediatranscoding.service.impl;

import com.tenacy.pixiescale.mediatranscoding.api.dto.TranscodingJobRequest;
import com.tenacy.pixiescale.mediatranscoding.domain.TranscodingJob;
import com.tenacy.pixiescale.mediatranscoding.domain.TranscodingTask;
import com.tenacy.pixiescale.mediatranscoding.service.MediaService;
import com.tenacy.pixiescale.mediatranscoding.service.TranscodingService;
import com.tenacy.pixiescale.mediatranscoding.service.TranscodingWorker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class TranscodingServiceImpl implements TranscodingService {

    private final MediaService mediaService;
    private final TranscodingWorker transcodingWorker;
    private final ConcurrentHashMap<String, TranscodingJob> jobStore = new ConcurrentHashMap<>();

    @Override
    public Mono<TranscodingJob> createJob(TranscodingJobRequest request) {
        return mediaService.getMediaInfo(request.getMediaFileId())
                .flatMap(mediaFile -> {
                    String jobId = UUID.randomUUID().toString();

                    // 트랜스코딩 작업 생성
                    TranscodingJob job = TranscodingJob.builder()
                            .id(jobId)
                            .mediaFileId(mediaFile.getId())
                            .status(TranscodingJob.JobStatus.PENDING)
                            .createdAt(LocalDateTime.now())
                            .tasks(new ArrayList<>())
                            .build();

                    // 요청된 해상도별 태스크 생성
                    List<TranscodingTask> tasks = new ArrayList<>();
                    request.getConfig().getResolutions().forEach(resolution -> {
                        TranscodingTask task = TranscodingTask.builder()
                                .id(UUID.randomUUID().toString())
                                .jobId(jobId)
                                .targetFormat(request.getConfig().getTargetFormat())
                                .targetWidth(resolution.getWidth())
                                .targetHeight(resolution.getHeight())
                                .targetBitrate(resolution.getBitrate())
                                .status(TranscodingTask.TaskStatus.PENDING)
                                .build();
                        tasks.add(task);
                    });

                    job.setTasks(tasks);
                    jobStore.put(jobId, job);

                    log.info("작업 생성 완료: jobId={}, 작업 수={}", jobId, tasks.size());

                    // 작업 시작
                    startJob(job);

                    return Mono.just(job);
                });
    }

    @Override
    public Mono<TranscodingJob> getJob(String jobId) {
        return Mono.fromCallable(() -> jobStore.get(jobId))
                .switchIfEmpty(Mono.error(new RuntimeException("작업을 찾을 수 없음: " + jobId)));
    }

    @Override
    public Flux<TranscodingJob> getJobsByMediaId(String mediaId) {
        return Flux.fromIterable(jobStore.values())
                .filter(job -> job.getMediaFileId().equals(mediaId));
    }

    @Override
    public Mono<Void> cancelJob(String jobId) {
        return getJob(jobId)
                .flatMap(job -> {
                    if (job.getStatus() == TranscodingJob.JobStatus.COMPLETED ||
                            job.getStatus() == TranscodingJob.JobStatus.FAILED) {
                        return Mono.error(new RuntimeException("이미 완료되거나 실패한 작업은 취소할 수 없음"));
                    }

                    job.setStatus(TranscodingJob.JobStatus.FAILED);
                    job.setErrorMessage("사용자에 의해 취소됨");
                    jobStore.put(jobId, job);

                    log.info("작업 취소됨: jobId={}", jobId);
                    return Mono.empty();
                });
    }

    private void startJob(TranscodingJob job) {
        job.setStatus(TranscodingJob.JobStatus.PROCESSING);
        job.setStartedAt(LocalDateTime.now());

        // 비동기로 모든 태스크 처리
        Flux.fromIterable(job.getTasks())
                .flatMap(task -> {
                    task.setStatus(TranscodingTask.TaskStatus.PROCESSING);
                    task.setStartedAt(LocalDateTime.now());

                    return transcodingWorker.processTask(task)
                            .doOnSuccess(processedTask -> {
                                // 태스크 완료 처리
                                processedTask.setStatus(TranscodingTask.TaskStatus.COMPLETED);
                                processedTask.setCompletedAt(LocalDateTime.now());
                                updateJobStatus(job);
                            })
                            .doOnError(error -> {
                                // 태스크 실패 처리
                                task.setStatus(TranscodingTask.TaskStatus.FAILED);
                                task.setErrorMessage(error.getMessage());
                                task.setCompletedAt(LocalDateTime.now());
                                updateJobStatus(job);

                                log.error("태스크 처리 실패: taskId={}, 오류={}", task.getId(), error.getMessage());
                            });
                })
                .subscribe();
    }

    private synchronized void updateJobStatus(TranscodingJob job) {
        // 모든 태스크 상태 확인
        boolean allCompleted = job.getTasks().stream()
                .allMatch(task -> task.getStatus() == TranscodingTask.TaskStatus.COMPLETED);

        boolean anyFailed = job.getTasks().stream()
                .anyMatch(task -> task.getStatus() == TranscodingTask.TaskStatus.FAILED);

        if (allCompleted) {
            job.setStatus(TranscodingJob.JobStatus.COMPLETED);
            job.setCompletedAt(LocalDateTime.now());
            log.info("모든 작업 완료: jobId={}", job.getId());
        } else if (anyFailed && job.getTasks().stream()
                .noneMatch(task -> task.getStatus() == TranscodingTask.TaskStatus.PROCESSING)) {
            job.setStatus(TranscodingJob.JobStatus.FAILED);
            job.setErrorMessage("일부 트랜스코딩 태스크 실패");
            job.setCompletedAt(LocalDateTime.now());
            log.warn("일부 작업 실패: jobId={}", job.getId());
        }

        jobStore.put(job.getId(), job);
    }
}