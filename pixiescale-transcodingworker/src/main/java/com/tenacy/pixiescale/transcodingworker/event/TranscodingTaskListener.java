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

        transcodingWorker.processTask(task)
                .flatMap(processedTask -> {
                    TaskResultEvent resultEvent = TaskResultEvent.builder()
                            .taskId(processedTask.getId())
                            .jobId(processedTask.getJobId())
                            .status(processedTask.getStatus().name())
                            .outputPath(processedTask.getOutputPath())
                            .completedAt(LocalDateTime.now())
                            .build();

                    return eventPublisher.publishTaskResult(resultEvent);
                })
                .onErrorResume(error -> {
                    TaskResultEvent resultEvent = TaskResultEvent.builder()
                            .taskId(task.getId())
                            .jobId(task.getJobId())
                            .status(TranscodingTask.TaskStatus.FAILED.name())
                            .errorMessage(error.getMessage())
                            .completedAt(LocalDateTime.now())
                            .build();

                    return eventPublisher.publishTaskResult(resultEvent);
                })
                .doFinally(signal -> {
                    ack.acknowledge();
                })
                .subscribeOn(transcodingScheduler)
                .subscribe();
    }
}