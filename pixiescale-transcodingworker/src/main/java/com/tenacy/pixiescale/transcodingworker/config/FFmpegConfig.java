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
}