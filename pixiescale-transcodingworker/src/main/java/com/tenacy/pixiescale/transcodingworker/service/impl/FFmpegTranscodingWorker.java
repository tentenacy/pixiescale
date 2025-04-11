package com.tenacy.pixiescale.transcodingworker.service.impl;

import com.tenacy.pixiescale.common.domain.TranscodingTask;
import com.tenacy.pixiescale.transcodingworker.config.FFmpegConfig;
import com.tenacy.pixiescale.transcodingworker.service.MetricsService;
import com.tenacy.pixiescale.transcodingworker.service.StorageService;
import com.tenacy.pixiescale.transcodingworker.service.TranscodingWorker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class FFmpegTranscodingWorker implements TranscodingWorker {

    private final FFmpegConfig ffmpegConfig;
    private final StorageService storageService;
    private final MetricsService metricsService;
    private final Scheduler transcodingScheduler;

    @Value("${app.media.source-dir}")
    private String sourceMediaDir;

    @Override
    public Mono<TranscodingTask> processTask(TranscodingTask task) {
        metricsService.taskReceived(task);

        return Mono.fromCallable(() -> {
                    // 작업 시작 시간 측정
                    Instant startTime = Instant.now();
                    metricsService.taskStarted(task);
                    log.info("트랜스코딩 작업 시작: {} (포맷: {}, 해상도: {}x{})",
                            task.getId(), task.getTargetFormat(), task.getTargetWidth(), task.getTargetHeight());

                    // 입력 파일 경로 결정
                    String mediaId = extractMediaId(task.getJobId());
                    Path mediaDir = Paths.get(sourceMediaDir);
                    Path inputPath = findMediaFile(mediaDir, mediaId);

                    if (inputPath == null) {
                        throw new RuntimeException("미디어 파일을 찾을 수 없음: " + mediaId);
                    }

                    // 입력 파일 크기 측정 (로깅용)
                    long fileSize = Files.size(inputPath);
                    log.info("입력 파일 크기: {} bytes", fileSize);

                    // 임시 출력 파일 생성
                    Path tempOutputPath = createTempFile(null, "output", task);

                    // FFmpeg 명령 구성 및 실행
                    List<String> command = buildFFmpegCommand(
                            inputPath.toString(),
                            tempOutputPath.toString(),
                            task
                    );

                    // FFmpeg 프로세스 실행 및 결과 처리
                    executeFFmpegCommand(command, tempOutputPath);

                    // 출력 파일 크기 측정
                    long outputSize = Files.size(tempOutputPath);
                    log.info("출력 파일 크기: {} bytes, 압축률: {}",
                            outputSize,
                            String.format("%.2f%%", (1 - (double)outputSize/fileSize) * 100));

                    // 출력 파일 이름 생성
                    String outputFilename = generateOutputFilename(task);

                    // 저장 경로 및 임시 파일 반환
                    return new Object[] {tempOutputPath, outputFilename, startTime, fileSize, outputSize};
                })
                .flatMap(resultArray -> {
                    Path tempOutputPath = (Path) resultArray[0];
                    String outputFilename = (String) resultArray[1];
                    Instant startTime = (Instant) resultArray[2];
                    long fileSize = (long) resultArray[3];
                    long outputSize = (long) resultArray[4];

                    // 저장소에 파일 저장
                    return storageService.store(tempOutputPath, outputFilename)
                            .doOnNext(storedPath -> {
                                // 태스크 상태 업데이트
                                task.setOutputPath(storedPath);
                                task.setStatus(TranscodingTask.TaskStatus.COMPLETED);
                                task.setCompletedAt(LocalDateTime.now());
                            })
                            .doOnSuccess(storedPath -> {
                                try {
                                    // 임시 파일 정리
                                    Files.deleteIfExists(tempOutputPath);
                                } catch (IOException e) {
                                    log.warn("임시 파일 삭제 실패", e);
                                }

                                // 작업 완료 메트릭 기록
                                Duration processingTime = Duration.between(startTime, Instant.now());
                                metricsService.taskCompleted(task, processingTime);
                                log.info("트랜스코딩 완료: {} - 입력: {} bytes, 출력: {} bytes, 소요시간: {} ms",
                                        task.getId(), fileSize, outputSize, processingTime.toMillis());
                            })
                            .thenReturn(task);
                })
                .onErrorResume(e -> {
                    log.error("트랜스코딩 작업 실패: {}", task.getId(), e);
                    task.setStatus(TranscodingTask.TaskStatus.FAILED);
                    task.setErrorMessage(e.getMessage());
                    task.setCompletedAt(LocalDateTime.now());
                    metricsService.taskFailed(task);
                    return Mono.just(task);
                })
                .subscribeOn(transcodingScheduler);
    }

    private void executeFFmpegCommand(List<String> command, Path tempOutputPath) throws Exception {
        log.debug("FFmpeg 명령 실행: {}", String.join(" ", command));

        ProcessBuilder processBuilder = new ProcessBuilder(command);
        processBuilder.redirectErrorStream(true);
        Process process = processBuilder.start();

        // 비동기로 출력 모니터링
        Thread outputMonitor = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                Pattern timePattern = Pattern.compile("time=([0-9:.]+)");
                Pattern speedPattern = Pattern.compile("speed=([0-9.]+)x");

                while ((line = reader.readLine()) != null) {
                    // 중요 정보만 로깅
                    if (line.contains("time=") && line.contains("speed=")) {
                        Matcher timeMatcher = timePattern.matcher(line);
                        Matcher speedMatcher = speedPattern.matcher(line);

                        if (timeMatcher.find() && speedMatcher.find()) {
                            log.debug("진행 상황 - 시간: {}, 속도: {}x",
                                    timeMatcher.group(1), speedMatcher.group(1));
                        }
                    } else if (line.contains("Error") || line.contains("error")) {
                        log.error("FFmpeg 오류: {}", line);
                    }
                }
            } catch (IOException e) {
                log.warn("FFmpeg 출력 모니터링 중 예외 발생", e);
            }
        });
        outputMonitor.setDaemon(true);
        outputMonitor.start();

        // 프로세스 완료 대기
        boolean completed = process.waitFor(ffmpegConfig.getTimeoutSeconds(), TimeUnit.SECONDS);
        if (!completed) {
            process.destroyForcibly();
            throw new RuntimeException("FFmpeg 처리 시간 초과");
        }

        int exitCode = process.exitValue();
        if (exitCode != 0) {
            throw new RuntimeException("FFmpeg 처리 실패: 종료 코드 " + exitCode);
        }
    }

    @Override
    public void initialize() {
        // FFmpeg 사용 가능 여부 확인
        try {
            ProcessBuilder processBuilder = new ProcessBuilder(ffmpegConfig.getBinaryPath(), "-version");
            Process process = processBuilder.start();
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new RuntimeException("FFmpeg 초기화 실패: 종료 코드 " + exitCode);
            }
            log.info("FFmpeg 초기화 완료");
        } catch (Exception e) {
            log.error("FFmpeg 초기화 실패", e);
            throw new RuntimeException("FFmpeg 초기화 실패", e);
        }

        // 소스 미디어 디렉토리 확인
        Path mediaPath = Paths.get(sourceMediaDir);
        if (!Files.exists(mediaPath)) {
            try {
                Files.createDirectories(mediaPath);
                log.info("소스 미디어 디렉토리 생성: {}", mediaPath);
            } catch (IOException e) {
                log.error("소스 미디어 디렉토리 생성 실패: {}", mediaPath, e);
                throw new RuntimeException("소스 미디어 디렉토리 생성 실패", e);
            }
        }
    }

    @Override
    public void shutdown() {
        log.info("FFmpeg 워커 종료");
    }

    private List<String> buildFFmpegCommand(String inputPath, String outputPath, TranscodingTask task) {
        List<String> command = new ArrayList<>();
        command.add(ffmpegConfig.getBinaryPath());

        // 메모리 최적화
        command.add("-threads");
        command.add(String.valueOf(ffmpegConfig.getThreadCount()));

        // GPU 가속 설정
        if (ffmpegConfig.isGpuAcceleration()) {
            command.add("-hwaccel");
            command.add("cuda");
            command.add("-hwaccel_device");
            command.add(ffmpegConfig.getGpuDevice());
        }

        // 입력 파일 분석 설정
        command.add("-analyzeduration");
        command.add(String.valueOf(ffmpegConfig.getAnalyzeDuration()));

        command.add("-probesize");
        command.add(ffmpegConfig.getBufferSize() + "M");

        // 스레드 큐 크기 설정
        command.add("-thread_queue_size");
        command.add(String.valueOf(ffmpegConfig.getThreadQueueSize()));

        // 입력 파일
        command.add("-i");
        command.add(inputPath);

        // 인코딩 설정
        command.add("-c:v");
        if (ffmpegConfig.isGpuAcceleration()) {
            // GPU 가속 인코더 설정
            if ("H.264".equalsIgnoreCase(task.getTargetFormat()) || "MP4".equalsIgnoreCase(task.getTargetFormat())) {
                command.add("h264_nvenc");
                command.add("-preset");
                command.add(ffmpegConfig.getGpuPreset());
            } else if ("H.265".equalsIgnoreCase(task.getTargetFormat()) || "HEVC".equalsIgnoreCase(task.getTargetFormat())) {
                command.add("hevc_nvenc");
                command.add("-preset");
                command.add(ffmpegConfig.getGpuPreset());
            }
        } else {
            // CPU 인코더 설정
            if ("H.264".equalsIgnoreCase(task.getTargetFormat()) || "MP4".equalsIgnoreCase(task.getTargetFormat())) {
                command.add("libx264");
                command.add("-preset");
                command.add(ffmpegConfig.getCpuPreset());
                command.add("-tune");
                command.add("fastdecode");
                command.add("-crf");
                command.add("28");
            } else if ("H.265".equalsIgnoreCase(task.getTargetFormat()) || "HEVC".equalsIgnoreCase(task.getTargetFormat())) {
                command.add("libx265");
                command.add("-preset");
                command.add(ffmpegConfig.getCpuPreset());
                command.add("-x265-params");
                command.add("log-level=error");
            } else if ("VP9".equalsIgnoreCase(task.getTargetFormat()) || "WebM".equalsIgnoreCase(task.getTargetFormat())) {
                command.add("libvpx-vp9");
                command.add("-speed");
                command.add("4");
                command.add("-tile-columns");
                command.add("2");
                command.add("-frame-parallel");
                command.add("1");
            } else {
                command.add("libx264");
                command.add("-preset");
                command.add(ffmpegConfig.getCpuPreset());
            }
        }

        // 해상도 설정
        command.add("-s");
        command.add(task.getTargetWidth() + "x" + task.getTargetHeight());

        // 비트레이트 설정
        command.add("-b:v");
        command.add(task.getTargetBitrate() + "k");
        command.add("-maxrate");
        command.add((int)(task.getTargetBitrate() * 1.2) + "k");
        command.add("-bufsize");
        command.add((int)(task.getTargetBitrate() * 1.5) + "k");

        // 오디오 설정
        command.add("-c:a");
        command.add("aac");
        command.add("-b:a");
        command.add("96k");
        command.add("-ar");
        command.add("44100");

        // 기타 설정
        command.add("-movflags");
        command.add("+faststart");
        command.add("-g");
        command.add("48");
        command.add("-sc_threshold");
        command.add("0");
        command.add("-y");

        command.add(outputPath);

        return command;
    }

    private Path createTempFile(Path sourcePath, String prefix, TranscodingTask task) throws IOException {
        Path tempDir = Paths.get(ffmpegConfig.getTempDir());
        if (!Files.exists(tempDir)) {
            Files.createDirectories(tempDir);
        }

        // 타겟 포맷에 맞는 확장자 사용
        String extension = getFileExtension(task.getTargetFormat());
        Path tempFile = Files.createTempFile(tempDir, prefix + "-", "." + extension);

        if (sourcePath != null && Files.exists(sourcePath)) {
            Files.copy(sourcePath, tempFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }

        return tempFile;
    }

    private String generateOutputFilename(TranscodingTask task) {
        return task.getJobId() + "-" + task.getTargetWidth() + "x" + task.getTargetHeight() + "." +
                getFileExtension(task.getTargetFormat());
    }

    private String getFileExtension(String format) {
        if ("H.264".equalsIgnoreCase(format) || "MP4".equalsIgnoreCase(format)) {
            return "mp4";
        } else if ("H.265".equalsIgnoreCase(format) || "HEVC".equalsIgnoreCase(format)) {
            return "mp4";
        } else if ("VP9".equalsIgnoreCase(format) || "WebM".equalsIgnoreCase(format)) {
            return "webm";
        } else {
            return "mp4"; // 기본값
        }
    }

    private String getMimeType(String format) {
        if ("H.264".equalsIgnoreCase(format) || "MP4".equalsIgnoreCase(format) ||
                "H.265".equalsIgnoreCase(format) || "HEVC".equalsIgnoreCase(format)) {
            return "video/mp4";
        } else if ("VP9".equalsIgnoreCase(format) || "WebM".equalsIgnoreCase(format)) {
            return "video/webm";
        } else {
            return "video/mp4"; // 기본값
        }
    }

    // 작업 ID에서 미디어 ID 추출 (형식: mediaId-jobUuid)
    private String extractMediaId(String jobId) {
        int separatorIndex = jobId.indexOf('-');
        if (separatorIndex > 0) {
            return jobId.substring(0, separatorIndex);
        }

        // 분리자가 없는 경우 전체 ID를 반환
        return jobId;
    }

    // 미디어 디렉토리에서 미디어 ID에 해당하는 파일 찾기
    private Path findMediaFile(Path mediaDir, String mediaId) throws IOException {
        if (!Files.exists(mediaDir)) {
            return null;
        }

        // 미디어 ID로 시작하는 파일 찾기 (예: mediaId-originalname.mp4)
        try (var files = Files.list(mediaDir)) {
            return files
                    .filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().startsWith(mediaId + "-"))
                    .findFirst()
                    .orElse(null);
        }
    }
}