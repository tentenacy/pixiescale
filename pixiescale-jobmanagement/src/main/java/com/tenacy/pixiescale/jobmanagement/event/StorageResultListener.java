package com.tenacy.pixiescale.jobmanagement.event;

import com.tenacy.pixiescale.jobmanagement.domain.TranscodingTask;
import com.tenacy.pixiescale.jobmanagement.service.impl.TranscodingServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class StorageResultListener {

    private final TranscodingServiceImpl transcodingService;

    @KafkaListener(topics = "${app.kafka.topics.storage-result}", groupId = "${spring.kafka.consumer.group-id}")
    public void handleStorageResult(StorageResultEvent event) {
        log.info("저장 결과 이벤트 수신: {} (성공: {})", event.getTaskId(), event.isSuccess());

        if (event.isSuccess()) {
            // 저장 성공 처리
            transcodingService.updateTaskStoragePath(
                    event.getTaskId(),
                    event.getStoragePath(),
                    event.getContentType()
            );
        } else {
            // 저장 실패 처리
            transcodingService.updateTaskStatus(
                    event.getTaskId(),
                    TranscodingTask.TaskStatus.FAILED,
                    null,
                    "저장 실패: " + event.getErrorMessage()
            );
        }
    }
}