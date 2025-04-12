package com.tenacy.pixiescale.mediaingestion.service.impl;

import com.tenacy.pixiescale.common.domain.MediaMetadata;
import com.tenacy.pixiescale.mediaingestion.config.FFmpegConfig;
import com.tenacy.pixiescale.mediaingestion.service.MetadataExtractor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class FFmpegMetadataExtractor implements MetadataExtractor {

    private final FFmpegConfig ffmpegConfig;

    @Override
    public Mono<MediaMetadata> extractMetadata(Path filePath) {
        return Mono.fromCallable(() -> {
            ProcessBuilder processBuilder = new ProcessBuilder(
                    ffmpegConfig.getBinaryPath(), "-i", filePath.toString(), "-hide_banner");
            processBuilder.redirectErrorStream(true);

            Process process = processBuilder.start();
            List<String> output = new ArrayList<>();

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.add(line);
                    log.debug("FFmpeg 출력: {}", line);
                }
            }

            int exitCode = process.waitFor();
            if (exitCode != 0 && exitCode != 1) { // FFmpeg returns 1 when querying information
                throw new RuntimeException("FFmpeg 처리 실패. 종료 코드: " + exitCode);
            }

            return parseMetadata(output);
        });
    }

    private MediaMetadata parseMetadata(List<String> ffmpegOutput) {
        MediaMetadata.MediaMetadataBuilder builder = MediaMetadata.builder();

        // 포맷 파싱
        Pattern formatPattern = Pattern.compile("Input #0,\\s+([^,]+),");
        for (String line : ffmpegOutput) {
            Matcher formatMatcher = formatPattern.matcher(line);
            if (formatMatcher.find()) {
                builder.format(formatMatcher.group(1).trim());
                break;
            }
        }

        // 비디오 스트림 정보 파싱
        Pattern videoPattern = Pattern.compile("Stream #\\d+:\\d+.*?Video:\\s+([^,]+),\\s+([^,]+),\\s+(\\d+)x(\\d+)");
        Pattern bitratePattern = Pattern.compile("(\\d+)\\s+kb/s");
        Pattern durationPattern = Pattern.compile("Duration:\\s+(\\d+):(\\d+):(\\d+\\.\\d+)");
        Pattern fpsPattern = Pattern.compile("(\\d+(?:\\.\\d+)?)\\s+fps");

        for (String line : ffmpegOutput) {
            // 코덱 및 차원 추출
            Matcher videoMatcher = videoPattern.matcher(line);
            if (videoMatcher.find()) {
                builder.codec(videoMatcher.group(1).trim());
                builder.width(Integer.parseInt(videoMatcher.group(3)));
                builder.height(Integer.parseInt(videoMatcher.group(4)));
            }

            // 비트레이트 추출
            Matcher bitrateMatcher = bitratePattern.matcher(line);
            if (bitrateMatcher.find()) {
                builder.bitrate(Integer.parseInt(bitrateMatcher.group(1)));
            }

            // fps 추출
            Matcher fpsMatcher = fpsPattern.matcher(line);
            if (fpsMatcher.find()) {
                builder.frameRate(Double.parseDouble(fpsMatcher.group(1)));
            }
        }

        // 길이 추출
        for (String line : ffmpegOutput) {
            Matcher durationMatcher = durationPattern.matcher(line);
            if (durationMatcher.find()) {
                int hours = Integer.parseInt(durationMatcher.group(1));
                int minutes = Integer.parseInt(durationMatcher.group(2));
                double seconds = Double.parseDouble(durationMatcher.group(3));
                double durationInSeconds = hours * 3600 + minutes * 60 + seconds;
                builder.duration(durationInSeconds);
                break;
            }
        }

        return builder.build();
    }
}