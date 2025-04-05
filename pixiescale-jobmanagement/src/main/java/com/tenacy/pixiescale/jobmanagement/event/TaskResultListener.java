package com.tenacy.pixiescale.jobmanagement.event;

import com.tenacy.pixiescale.common.domain.TranscodingTask;
import com.tenacy.pixiescale.common.event.TaskResultEvent;
import com.tenacy.pixiescale.jobmanagement.service.impl.TranscodingServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class TaskResultListener {

    private final TranscodingServiceImpl transcodingService;

    @KafkaListener(topics = "${app.kafka.topics.task-result}", groupId = "${spring.kafka.consumer.group-id}")
    public void handleTaskResult(TaskResultEvent event) {
        log.info("트랜스코딩 태스크 결과 이벤트 수신: {}", event.getTaskId());

        TranscodingTask.TaskStatus status = TranscodingTask.TaskStatus.valueOf(event.getStatus());
        transcodingService.updateTaskStatus(
                event.getTaskId(),
                status,
                event.getOutputPath(),
                event.getErrorMessage()
        );
    }
}