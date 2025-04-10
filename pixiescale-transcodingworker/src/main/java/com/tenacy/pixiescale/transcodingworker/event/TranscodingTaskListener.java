package com.tenacy.pixiescale.transcodingworker.event;

import com.tenacy.pixiescale.common.domain.TranscodingTask;
import com.tenacy.pixiescale.common.event.TaskResultEvent;
import com.tenacy.pixiescale.common.event.TranscodingTaskEvent;
import com.tenacy.pixiescale.transcodingworker.service.EventPublisher;
import com.tenacy.pixiescale.transcodingworker.service.TranscodingWorker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import reactor.core.scheduler.Scheduler;

import java.time.LocalDateTime;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Component
@RequiredArgsConstructor
public class TranscodingTaskListener {

    private final TranscodingWorker transcodingWorker;
    private final EventPublisher eventPublisher;
    private final Scheduler transcodingScheduler;

    // 동시 실행 중인 작업 수 추적
    private final AtomicInteger activeTasksCount = new AtomicInteger(0);
    // 작업 ID를 키로 사용하는 활성 작업 맵
    private final ConcurrentHashMap<String, TranscodingTask> activeTasks = new ConcurrentHashMap<>();

    @KafkaListener(topics = "${app.kafka.topics.transcoding-task}",
            groupId = "${spring.kafka.consumer.group-id}",
            containerFactory = "kafkaListenerContainerFactory")
    public void handleTranscodingTask(TranscodingTaskEvent event, Acknowledgment ack) {
        log.info("Received transcoding task event: {}", event.getTaskId());

        TranscodingTask task = TranscodingTask.builder()
                .id(event.getTaskId())
                .jobId(event.getJobId())
                .targetFormat(event.getTargetFormat())
                .targetWidth(event.getTargetWidth())
                .targetHeight(event.getTargetHeight())
                .targetBitrate(event.getTargetBitrate())
                .status(TranscodingTask.TaskStatus.PROCESSING)
                .startedAt(LocalDateTime.now())
                .build();

        // 활성 작업 맵에 추가
        activeTasks.put(task.getId(), task);
        activeTasksCount.incrementAndGet();

        transcodingWorker.processTask(task)
                .doOnSuccess(processedTask -> {
                    TaskResultEvent resultEvent = TaskResultEvent.builder()
                            .taskId(processedTask.getId())
                            .jobId(processedTask.getJobId())
                            .status(processedTask.getStatus().name())
                            .outputPath(processedTask.getOutputPath())
                            .completedAt(LocalDateTime.now())
                            .build();

                    eventPublisher.publishTaskResult(resultEvent)
                            .doFinally(signal -> {
                                // 작업 완료 후 상태 정리 및 오프셋 커밋
                                cleanupTask(task.getId());
                                ack.acknowledge();
                            })
                            .subscribe();
                })
                .doOnError(error -> {
                    TaskResultEvent resultEvent = TaskResultEvent.builder()
                            .taskId(task.getId())
                            .jobId(task.getJobId())
                            .status(TranscodingTask.TaskStatus.FAILED.name())
                            .errorMessage(error.getMessage())
                            .completedAt(LocalDateTime.now())
                            .build();

                    eventPublisher.publishTaskResult(resultEvent)
                            .doFinally(signal -> {
                                // 작업 실패 후 상태 정리 및 오프셋 커밋
                                cleanupTask(task.getId());
                                ack.acknowledge();
                            })
                            .subscribe();
                })
                .subscribeOn(transcodingScheduler)
                .subscribe();
    }

    private void cleanupTask(String taskId) {
        activeTasks.remove(taskId);
        activeTasksCount.decrementAndGet();
    }
}