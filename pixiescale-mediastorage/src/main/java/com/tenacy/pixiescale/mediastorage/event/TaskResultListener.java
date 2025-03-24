package com.tenacy.pixiescale.mediastorage.event;

import com.tenacy.pixiescale.mediastorage.service.EventPublisher;
import com.tenacy.pixiescale.mediastorage.service.StorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.FileSystemResource;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;

@Slf4j
@Component
@RequiredArgsConstructor
public class TaskResultListener {

    private final StorageService storageService;
    private final EventPublisher eventPublisher;

    // pixiescale-mediastorage/src/main/java/com/tenacy/pixiescale/mediastorage/event/TaskResultListener.java (계속)
    @KafkaListener(topics = "${app.kafka.topics.task-result}", groupId = "${spring.kafka.consumer.group-id}")
    public void handleTaskResult(TaskResultEvent event) {
        log.info("트랜스코딩 태스크 결과 이벤트 수신: {}", event.getTaskId());

        // 실패한 작업은 무시
        if (!"COMPLETED".equals(event.getStatus())) {
            log.info("태스크 {}가 성공적으로 완료되지 않았습니다. 무시합니다.", event.getTaskId());
            return;
        }

        // 트랜스코딩된 파일을 영구 저장소로 이동
        String outputPath = event.getOutputPath();
        if (outputPath == null || outputPath.isEmpty()) {
            log.error("출력 경로가 없습니다. 태스크: {}", event.getTaskId());
            publishFailureEvent(event, "출력 경로가 없습니다");
            return;
        }

        Path sourcePath = Paths.get(outputPath);
        File sourceFile = sourcePath.toFile();

        if (!sourceFile.exists() || !sourceFile.isFile()) {
            log.error("출력 파일이 존재하지 않습니다: {}", outputPath);
            publishFailureEvent(event, "출력 파일이 존재하지 않습니다: " + outputPath);
            return;
        }

        String fileName = sourcePath.getFileName().toString();
        String contentType = determineContentType(fileName);

        try {
            // 파일을 영구 저장소로 복사
            storageService.store(sourcePath, fileName)
                    .doOnSuccess(storedPath -> {
                        log.info("파일이 영구 저장소에 성공적으로 저장되었습니다: {}", storedPath);

                        // 저장 결과 이벤트 발행
                        StorageResultEvent resultEvent = StorageResultEvent.builder()
                                .taskId(event.getTaskId())
                                .jobId(event.getJobId())
                                .storagePath(storedPath)
                                .contentType(contentType)
                                .success(true)
                                .build();

                        eventPublisher.publishStorageResult(resultEvent)
                                .subscribe(
                                        VoidUnused -> log.debug("저장 결과 이벤트 발행 성공: {}", event.getTaskId()),
                                        error -> log.error("저장 결과 이벤트 발행 실패: {}", event.getTaskId(), error)
                                );

                        // 원본 파일 삭제 (선택 사항)
                        try {
                            if (sourceFile.delete()) {
                                log.debug("원본 파일 삭제 성공: {}", outputPath);
                            } else {
                                log.warn("원본 파일 삭제 실패: {}", outputPath);
                            }
                        } catch (Exception e) {
                            log.warn("원본 파일 삭제 중 오류 발생: {}", outputPath, e);
                        }
                    })
                    .doOnError(error -> {
                        log.error("파일 저장 실패: {}", error.getMessage(), error);
                        publishFailureEvent(event, "파일 저장 실패: " + error.getMessage());
                    })
                    .subscribe();
        } catch (Exception e) {
            log.error("태스크 결과 처리 중 오류 발생: {}", e.getMessage(), e);
            publishFailureEvent(event, "태스크 결과 처리 실패: " + e.getMessage());
        }
    }

    private void publishFailureEvent(TaskResultEvent event, String errorMessage) {
        StorageResultEvent resultEvent = StorageResultEvent.builder()
                .taskId(event.getTaskId())
                .jobId(event.getJobId())
                .success(false)
                .errorMessage(errorMessage)
                .build();

        eventPublisher.publishStorageResult(resultEvent)
                .subscribe(
                        VoidUnused -> log.debug("실패 이벤트 발행 성공: {}", event.getTaskId()),
                        error -> log.error("실패 이벤트 발행 실패: {}", event.getTaskId(), error)
                );
    }

    private String determineContentType(String filePath) {
        String extension = filePath.substring(filePath.lastIndexOf('.') + 1).toLowerCase();

        switch (extension) {
            case "mp4":
                return "video/mp4";
            case "webm":
                return "video/webm";
            case "mkv":
                return "video/x-matroska";
            case "avi":
                return "video/x-msvideo";
            case "mov":
                return "video/quicktime";
            case "wmv":
                return "video/x-ms-wmv";
            case "flv":
                return "video/x-flv";
            case "m4v":
                return "video/x-m4v";
            case "m3u8":
                return "application/x-mpegURL";
            case "ts":
                return "video/MP2T";
            case "3gp":
                return "video/3gpp";
            case "mpg":
            case "mpeg":
                return "video/mpeg";
            default:
                return "application/octet-stream";
        }
    }
}