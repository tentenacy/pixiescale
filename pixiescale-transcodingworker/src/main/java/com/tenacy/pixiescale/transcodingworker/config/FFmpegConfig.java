package com.tenacy.pixiescale.transcodingworker.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "ffmpeg")
public class FFmpegConfig {
    private String binaryPath = "ffmpeg";
    private String tempDir = "/tmp";
    private int timeoutSeconds = 3600;
    private boolean gpuAcceleration = false;
    private String gpuDevice = "0";
    private int threadCount = 0; // 0은 자동 감지
    private String cpuPreset = "medium"; // ultrafast, superfast, veryfast, faster, fast, medium, slow, slower, veryslow
    private String gpuPreset = "p4"; // p1-p7 (NVIDIA 프리셋)
    private int bufferSize = 16; // 버퍼 크기 (MB)
}