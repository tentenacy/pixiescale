package com.tenacy.pixiescale.transcodingworker.service.impl;

import com.tenacy.pixiescale.transcodingworker.config.FFmpegConfig;
import com.tenacy.pixiescale.common.domain.TranscodingTask;
import com.tenacy.pixiescale.transcodingworker.service.StorageService;
import com.tenacy.pixiescale.transcodingworker.service.TranscodingWorker;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
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
    private final MeterRegistry meterRegistry;;

    @Value("${app.media.source-dir}")
    private String sourceMediaDir;

    @Override
    public Mono<TranscodingTask> processTask(TranscodingTask task) {
        return Mono.<TranscodingTask>create(sink -> {
            Timer.Sample sample = Timer.start(meterRegistry);

            try {
                // 작업 시작 시간 측정
                long startTime = System.currentTimeMillis();
                log.info("트랜스코딩 작업 시작: {} (포맷: {}, 해상도: {}x{})",
                        task.getId(), task.getTargetFormat(), task.getTargetWidth(), task.getTargetHeight());

                // 입력 파일 경로 결정 (작업 ID에서 미디어 ID 추출)
                String mediaId = extractMediaId(task.getJobId());
                Path mediaDir = Paths.get(sourceMediaDir);
                Path inputPath = findMediaFile(mediaDir, mediaId);

                if (inputPath == null) {
                    sink.error(new RuntimeException("미디어 파일을 찾을 수 없음: " + mediaId));
                    return;
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

                log.debug("FFmpeg 명령 실행: {}", String.join(" ", command));

                // FFmpeg 프로세스 실행 - 진행률 모니터링 추가
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
                            // 주기적으로 중요 정보만 로깅 (모든 라인을 로깅하지 않음)
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

                // 프로세스 완료 대기 - 시간 제한 설정
                boolean completed = process.waitFor(ffmpegConfig.getTimeoutSeconds(), TimeUnit.SECONDS);
                if (!completed) {
                    process.destroyForcibly();
                    sink.error(new RuntimeException("FFmpeg 처리 시간 초과"));
                    return;
                }

                int exitCode = process.exitValue();
                if (exitCode != 0) {
                    sink.error(new RuntimeException("FFmpeg 처리 실패: 종료 코드 " + exitCode));
                    return;
                }

                // 출력 파일 크기 측정 및 결과 로깅
                long outputSize = Files.size(tempOutputPath);
                long processingTime = System.currentTimeMillis() - startTime;

                log.info("트랜스코딩 완료: {} - 입력: {} bytes, 출력: {} bytes, 압축률: {}, 소요시간: {} ms",
                        task.getId(), fileSize, outputSize,
                        String.format("%.2f%%", (1 - (double)outputSize/fileSize) * 100),
                        processingTime);

                // 출력 파일을 저장소에 저장
                String outputFilename = generateOutputFilename(task);

                // 저장 작업 수행
                storageService.store(tempOutputPath, outputFilename)
                        .subscribe(
                                storedPath -> {
                                    try {
                                        // 임시 파일 정리 (입력 파일은 공유 리소스이므로 삭제하지 않음)
                                        Files.deleteIfExists(tempOutputPath);

                                        // 태스크 업데이트 및 완료
                                        task.setOutputPath(storedPath);
                                        task.setStatus(TranscodingTask.TaskStatus.COMPLETED);
                                        task.setCompletedAt(java.time.LocalDateTime.now());
                                        sink.success(task);
                                    } catch (IOException e) {
                                        log.warn("임시 파일 삭제 실패", e);
                                        sink.success(task); // 파일 삭제 실패해도 태스크는 성공으로 처리
                                    }
                                },
                                error -> {
                                    try {
                                        Files.deleteIfExists(tempOutputPath);
                                    } catch (IOException e) {
                                        log.warn("임시 파일 삭제 실패", e);
                                    }
                                    sink.error(error);
                                }
                        );

                // 성공 시 타이머 기록
                sample.stop(Timer.builder("transcoding.duration")
                        .tag("status", "success")
                        .tag("format", task.getTargetFormat())
                        .tag("resolution", task.getTargetWidth() + "x" + task.getTargetHeight())
                        .register(meterRegistry));

                // 작업 결과 반환
                sink.success(task);
            } catch (Exception e) {
                sample.stop(Timer.builder("transcoding.duration")
                        .tag("status", "failed")
                        .tag("format", task.getTargetFormat())
                        .tag("resolution", task.getTargetWidth() + "x" + task.getTargetHeight())
                        .register(meterRegistry));

                sink.error(e);
            }
        }).subscribeOn(Schedulers.boundedElastic()); // 전용 스레드 풀에서 실행
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
        // 리소스 정리 (필요 시)
        log.info("FFmpeg 워커 종료");
    }

    private List<String> buildFFmpegCommand(String inputPath, String outputPath, TranscodingTask task) {
        List<String> command = new ArrayList<>();
        command.add(ffmpegConfig.getBinaryPath());

        // GPU 가속 설정 - 입력 파일 전에 배치
        if (ffmpegConfig.isGpuAcceleration()) {
            command.add("-hwaccel");
            command.add("cuda");
            command.add("-hwaccel_device");
            command.add(ffmpegConfig.getGpuDevice());
        }

        // 입력 파일 분석 설정
        command.add("-analyzeduration");
        command.add("10000000");  // 10초
        command.add("-probesize");
        command.add(ffmpegConfig.getBufferSize() + "M");

        // 스레드 큐 크기
        command.add("-thread_queue_size");
        command.add("512");

        // 입력 파일
        command.add("-i");
        command.add(inputPath);

        // 인코딩 설정
        if (ffmpegConfig.isGpuAcceleration()) {
            // GPU 가속 인코더 선택
            if ("H.264".equalsIgnoreCase(task.getTargetFormat()) || "MP4".equalsIgnoreCase(task.getTargetFormat())) {
                command.add("-c:v");
                command.add("h264_nvenc");
                command.add("-preset");
                command.add(ffmpegConfig.getGpuPreset());
            } else if ("H.265".equalsIgnoreCase(task.getTargetFormat()) || "HEVC".equalsIgnoreCase(task.getTargetFormat())) {
                command.add("-c:v");
                command.add("hevc_nvenc");
                command.add("-preset");
                command.add(ffmpegConfig.getGpuPreset());
            }
        } else {
            // 기존 CPU 인코딩 설정 그대로 유지
            command.add("-c:v");
            if ("H.264".equalsIgnoreCase(task.getTargetFormat()) || "MP4".equalsIgnoreCase(task.getTargetFormat())) {
                command.add("libx264");
                command.add("-preset");
                command.add(ffmpegConfig.getCpuPreset());
                command.add("-tune");
                command.add("fastdecode");
            } else if ("H.265".equalsIgnoreCase(task.getTargetFormat()) || "HEVC".equalsIgnoreCase(task.getTargetFormat())) {
                command.add("libx265");
                command.add("-preset");
                command.add(ffmpegConfig.getCpuPreset());
                command.add("-x265-params");
                command.add("log-level=error");
            } else if ("VP9".equalsIgnoreCase(task.getTargetFormat()) || "WebM".equalsIgnoreCase(task.getTargetFormat())) {
                command.add("libvpx-vp9");
                command.add("-speed");
                command.add("2");
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

        // 나머지 설정은 그대로 유지
        int threads = ffmpegConfig.getThreadCount();
        if (threads <= 0) {
            threads = Runtime.getRuntime().availableProcessors();
        }
        command.add("-threads");
        command.add(String.valueOf(threads));

        command.add("-s");
        command.add(task.getTargetWidth() + "x" + task.getTargetHeight());

        command.add("-b:v");
        command.add(task.getTargetBitrate() + "k");
        command.add("-maxrate");
        command.add((int)(task.getTargetBitrate() * 1.5) + "k");
        command.add("-bufsize");
        command.add((task.getTargetBitrate() * 2) + "k");

        command.add("-c:a");
        command.add("aac");
        command.add("-b:a");
        command.add("128k");
        command.add("-ar");
        command.add("48000");

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