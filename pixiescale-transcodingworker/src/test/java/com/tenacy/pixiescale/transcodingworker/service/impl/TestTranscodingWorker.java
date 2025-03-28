package com.tenacy.pixiescale.transcodingworker.service.impl;

import com.tenacy.pixiescale.transcodingworker.config.FFmpegConfig;
import com.tenacy.pixiescale.transcodingworker.domain.TranscodingTask;
import com.tenacy.pixiescale.transcodingworker.service.StorageService;
import com.tenacy.pixiescale.transcodingworker.service.TranscodingWorker;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

@Service
@Profile("test")
@Primary
public class TestTranscodingWorker implements TranscodingWorker {

    private final FFmpegConfig ffmpegConfig;
    private final StorageService storageService;

    public TestTranscodingWorker(FFmpegConfig ffmpegConfig, StorageService storageService) {
        this.ffmpegConfig = ffmpegConfig;
        this.storageService = storageService;
    }

    @Override
    public Mono<TranscodingTask> processTask(TranscodingTask task) {
        // 실제 FFmpeg 실행 없이 결과 시뮬레이션
        task.setStatus(TranscodingTask.TaskStatus.COMPLETED);
        task.setOutputPath("test-output.mp4");
        task.setCompletedAt(LocalDateTime.now());

        // 저장소 서비스 호출 (실제 저장은 모킹됨)
        return storageService.store(
                        java.nio.file.Paths.get(System.getProperty("java.io.tmpdir"), "test-output.mp4"),
                        "test-output.mp4"
                )
                .map(storedPath -> {
                    task.setOutputPath(storedPath);
                    return task;
                });
    }

    @Override
    public void initialize() {
        // 실제 초기화 없이 성공으로 처리
        System.out.println("Test TranscodingWorker initialized");
    }

    @Override
    public void shutdown() {
        // 아무 작업 없음
        System.out.println("Test TranscodingWorker shutdown");
    }
}