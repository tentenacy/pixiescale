package com.tenacy.pixiescale.jobmanagement.service.impl;

import com.tenacy.pixiescale.jobmanagement.api.dto.TranscodingJobRequest;
import com.tenacy.pixiescale.jobmanagement.domain.MediaFile;
import com.tenacy.pixiescale.jobmanagement.domain.TranscodingJob;
import com.tenacy.pixiescale.jobmanagement.domain.TranscodingTask;
import com.tenacy.pixiescale.jobmanagement.event.MediaEventListener;
import com.tenacy.pixiescale.jobmanagement.event.TranscodingJobEvent;
import com.tenacy.pixiescale.jobmanagement.event.TranscodingTaskEvent;
import com.tenacy.pixiescale.jobmanagement.service.EventPublisher;
import com.tenacy.pixiescale.jobmanagement.service.TranscodingService;
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

    private final EventPublisher eventPublisher;
    private final MediaEventListener mediaEventListener;
    private final ConcurrentHashMap<String, TranscodingJob> jobStore = new ConcurrentHashMap<>();

    @Override
    public Mono<TranscodingJob> createJob(TranscodingJobRequest request) {
        return Mono.fromCallable(() -> {
            String mediaFileId = request.getMediaFileId();

            // 미디어 정보 조회
            MediaFile mediaFile = mediaEventListener.getMediaInfo(mediaFileId);
            if (mediaFile == null) {
                throw new RuntimeException("미디어 파일을 찾을 수 없음: " + mediaFileId);
            }

            String jobId = mediaFileId + "-" + UUID.randomUUID();

            // 트랜스코딩 작업 생성
            TranscodingJob job = TranscodingJob.builder()
                    .id(jobId)
                    .mediaFileId(mediaFileId)
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

            // 작업 생성 이벤트 발행
            TranscodingJobEvent jobEvent = TranscodingJobEvent.builder()
                    .jobId(job.getId())
                    .mediaFileId(job.getMediaFileId())
                    .status(job.getStatus())
                    .config(request.getConfig())
                    .timestamp(LocalDateTime.now())
                    .build();

            eventPublisher.publishJobCreated(jobEvent).subscribe();

            // 작업 시작
            startJob(job);

            return job;
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

                    // 작업 상태 변경 이벤트 발행
                    TranscodingJobEvent updateEvent = TranscodingJobEvent.builder()
                            .jobId(job.getId())
                            .mediaFileId(job.getMediaFileId())
                            .status(job.getStatus())
                            .timestamp(LocalDateTime.now())
                            .build();

                    eventPublisher.publishJobUpdated(updateEvent).subscribe();

                    log.info("작업 취소됨: jobId={}", jobId);
                    return Mono.empty();
                });
    }

    private void startJob(TranscodingJob job) {
        job.setStatus(TranscodingJob.JobStatus.PROCESSING);
        job.setStartedAt(LocalDateTime.now());

        // 작업 상태 변경 이벤트 발행
        TranscodingJobEvent updateEvent = TranscodingJobEvent.builder()
                .jobId(job.getId())
                .mediaFileId(job.getMediaFileId())
                .status(job.getStatus())
                .timestamp(LocalDateTime.now())
                .build();

        eventPublisher.publishJobUpdated(updateEvent).subscribe();

        // 각 태스크별 이벤트 발행
        job.getTasks().forEach(task -> {
            task.setStatus(TranscodingTask.TaskStatus.PENDING);

            TranscodingTaskEvent taskEvent = TranscodingTaskEvent.builder()
                    .taskId(task.getId())
                    .jobId(job.getId())
                    .targetFormat(task.getTargetFormat())
                    .targetWidth(task.getTargetWidth())
                    .targetHeight(task.getTargetHeight())
                    .targetBitrate(task.getTargetBitrate())
                    .build();

            eventPublisher.publishTranscodingTask(taskEvent)
                    .subscribe(
                            VoidUnused -> log.debug("트랜스코딩 태스크 이벤트 발행 성공: {}", task.getId()),
                            error -> log.error("트랜스코딩 태스크 이벤트 발행 실패: {}", task.getId(), error)
                    );
        });
    }

    public void updateTaskStoragePath(String taskId, String storagePath, String contentType) {
        // 태스크 ID로 해당 작업 찾기
        jobStore.values().stream()
                .flatMap(job -> job.getTasks().stream())
                .filter(task -> task.getId().equals(taskId))
                .findFirst()
                .ifPresent(task -> {
                    // 태스크 정보 업데이트
                    task.setOutputPath(storagePath);
                    // 추가 필드가 있다면 여기서 설정 (예: contentType)

                    // 해당 태스크가 속한 작업 찾기
                    jobStore.values().stream()
                            .filter(job -> job.getId().equals(task.getJobId()))
                            .findFirst()
                            .ifPresent(this::updateJobStatus);
                });
    }

    // 트랜스코딩 작업 결과 처리를 위한 메서드 (이벤트 리스너에서 호출)
    public void updateTaskStatus(String taskId, TranscodingTask.TaskStatus status, String outputPath, String errorMessage) {
        // 태스크 ID로 해당 작업 찾기
        jobStore.values().stream()
                .flatMap(job -> job.getTasks().stream())
                .filter(task -> task.getId().equals(taskId))
                .findFirst()
                .ifPresent(task -> {
                    task.setStatus(status);
                    task.setCompletedAt(LocalDateTime.now());

                    if (status == TranscodingTask.TaskStatus.COMPLETED) {
                        task.setOutputPath(outputPath);
                    } else if (status == TranscodingTask.TaskStatus.FAILED) {
                        task.setErrorMessage(errorMessage);
                    }

                    // 해당 태스크가 속한 작업 찾기
                    jobStore.values().stream()
                            .filter(job -> job.getId().equals(task.getJobId()))
                            .findFirst()
                            .ifPresent(this::updateJobStatus);
                });
    }

    private synchronized void updateJobStatus(TranscodingJob job) {
        // 모든 태스크 상태 확인
        boolean allCompleted = job.getTasks().stream()
                .allMatch(task -> task.getStatus() == TranscodingTask.TaskStatus.COMPLETED);

        boolean anyFailed = job.getTasks().stream()
                .anyMatch(task -> task.getStatus() == TranscodingTask.TaskStatus.FAILED);

        boolean anyProcessing = job.getTasks().stream()
                .anyMatch(task -> task.getStatus() == TranscodingTask.TaskStatus.PROCESSING);

        TranscodingJob.JobStatus oldStatus = job.getStatus();

        if (allCompleted) {
            job.setStatus(TranscodingJob.JobStatus.COMPLETED);
            job.setCompletedAt(LocalDateTime.now());
            log.info("모든 작업 완료: jobId={}", job.getId());
        } else if (anyFailed && !anyProcessing) {
            job.setStatus(TranscodingJob.JobStatus.FAILED);
            job.setErrorMessage("일부 트랜스코딩 태스크 실패");
            job.setCompletedAt(LocalDateTime.now());
            log.warn("일부 작업 실패: jobId={}", job.getId());
        }

        jobStore.put(job.getId(), job);

        // 작업 상태가 변경된 경우에만 이벤트 발행
        if (oldStatus != job.getStatus()) {
            TranscodingJobEvent updateEvent = TranscodingJobEvent.builder()
                    .jobId(job.getId())
                    .mediaFileId(job.getMediaFileId())
                    .status(job.getStatus())
                    .timestamp(LocalDateTime.now())
                    .build();

            eventPublisher.publishJobUpdated(updateEvent).subscribe();
        }
    }
}