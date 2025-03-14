package com.tenacy.pixiescale.mediatranscoding.service.impl;

import com.tenacy.pixiescale.mediatranscoding.config.FFmpegConfig;
import com.tenacy.pixiescale.mediatranscoding.domain.TranscodingTask;
import com.tenacy.pixiescale.mediatranscoding.service.MediaService;
import com.tenacy.pixiescale.mediatranscoding.service.StorageService;
import com.tenacy.pixiescale.mediatranscoding.service.TranscodingWorker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class FFmpegTranscodingWorker implements TranscodingWorker {

    private final FFmpegConfig ffmpegConfig;
    private final MediaService mediaService;
    private final StorageService storageService;

    @Override
    public Mono<TranscodingTask> processTask(TranscodingTask task) {
        // 정확한 미디어 ID 추출
        String mediaId = task.getJobId().split("-")[0];

        return mediaService.getMediaInfo(mediaId)
                .flatMap(mediaFile -> mediaService.getMediaResource(mediaFile.getId())
                        .flatMap(inputResource -> {
                            return Mono.<TranscodingTask>create(sink -> {
                                try {
                                    // 임시 입력 파일 생성
                                    Path tempInputPath = createTempFile(inputResource, "input");
                                    Path tempOutputPath = createTempFile(null, "output");

                                    // FFmpeg 명령 구성 및 실행
                                    List<String> command = buildFFmpegCommand(
                                            tempInputPath.toString(),
                                            tempOutputPath.toString(),
                                            task
                                    );

                                    log.info("FFmpeg 명령 실행: {}", String.join(" ", command));

                                    // FFmpeg 프로세스 실행
                                    ProcessBuilder processBuilder = new ProcessBuilder(command);
                                    processBuilder.redirectErrorStream(true);
                                    Process process = processBuilder.start();

                                    // 출력 로그 캡처
                                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                                        String line;
                                        while ((line = reader.readLine()) != null) {
                                            log.debug("FFmpeg 출력: {}", line);
                                        }
                                    }

                                    // 프로세스 완료 대기
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

                                    // 출력 파일을 저장소에 저장
                                    String outputFilename = generateOutputFilename(task);

                                    // 저장 작업 수행
                                    storageService.store(tempOutputPath, outputFilename)
                                            .subscribe(
                                                    storedPath -> {
                                                        try {
                                                            // 임시 파일 정리
                                                            Files.deleteIfExists(tempInputPath);
                                                            Files.deleteIfExists(tempOutputPath);

                                                            // 태스크 업데이트 및 완료
                                                            task.setOutputPath(storedPath);
                                                            sink.success(task);
                                                        } catch (IOException e) {
                                                            log.warn("임시 파일 삭제 실패", e);
                                                            sink.success(task); // 파일 삭제 실패해도 태스크는 성공으로 처리
                                                        }
                                                    },
                                                    error -> {
                                                        try {
                                                            Files.deleteIfExists(tempInputPath);
                                                            Files.deleteIfExists(tempOutputPath);
                                                        } catch (IOException e) {
                                                            log.warn("임시 파일 삭제 실패", e);
                                                        }
                                                        sink.error(error);
                                                    }
                                            );
                                } catch (Exception e) {
                                    sink.error(e);
                                }
                            });
                        }));
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
    }

    @Override
    public void shutdown() {
        // 리소스 정리 (필요 시)
        log.info("FFmpeg 워커 종료");
    }

    private List<String> buildFFmpegCommand(String inputPath, String outputPath, TranscodingTask task) {
        List<String> command = new ArrayList<>();
        command.add(ffmpegConfig.getBinaryPath());
        command.add("-i");
        command.add(inputPath);

        // 인코딩 설정
        if (ffmpegConfig.isGpuAcceleration()) {
            // GPU 가속 (NVIDIA CUDA)
            command.add("-hwaccel");
            command.add("cuda");
            command.add("-hwaccel_device");
            command.add(ffmpegConfig.getGpuDevice());
        }

        // 비디오 코덱 설정
        command.add("-c:v");
        if ("H.264".equalsIgnoreCase(task.getTargetFormat()) || "MP4".equalsIgnoreCase(task.getTargetFormat())) {
            command.add("libx264");
        } else if ("H.265".equalsIgnoreCase(task.getTargetFormat()) || "HEVC".equalsIgnoreCase(task.getTargetFormat())) {
            command.add("libx265");
        } else if ("VP9".equalsIgnoreCase(task.getTargetFormat()) || "WebM".equalsIgnoreCase(task.getTargetFormat())) {
            command.add("libvpx-vp9");
        } else {
            command.add("libx264"); // 기본값
        }

        // 해상도 설정
        command.add("-s");
        command.add(task.getTargetWidth() + "x" + task.getTargetHeight());

        // 비트레이트 설정
        command.add("-b:v");
        command.add(task.getTargetBitrate() + "k");

        // 오디오 설정
        command.add("-c:a");
        command.add("aac");
        command.add("-b:a");
        command.add("128k");

        // 기타 설정
        command.add("-movflags");
        command.add("+faststart");
        command.add("-y"); // 출력 파일 덮어쓰기

        // 출력 파일
        command.add(outputPath);

        return command;
    }

    private Path createTempFile(Resource resource, String prefix) throws IOException {
        Path tempDir = Paths.get(ffmpegConfig.getTempDir());
        if (!Files.exists(tempDir)) {
            Files.createDirectories(tempDir);
        }

        Path tempFile = Files.createTempFile(tempDir, prefix + "-", ".tmp");

        if (resource != null) {
            Files.copy(resource.getInputStream(), tempFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
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
}