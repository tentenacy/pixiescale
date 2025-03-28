package com.tenacy.pixiescale.transcodingworker.service.impl;

import com.tenacy.pixiescale.transcodingworker.service.HealthCheckService;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
public class FFmpegHealthCheckService implements HealthCheckService {

    @Override
    public Map<String, Object> checkFFmpegHealth(String ffmpegPath) {
        Map<String, Object> health = new HashMap<>();

        try {
            ProcessBuilder processBuilder = new ProcessBuilder(ffmpegPath, "-version");
            Process process = processBuilder.start();
            int exitCode = process.waitFor();

            if (exitCode == 0) {
                health.put("ffmpeg", "UP");
            } else {
                health.put("ffmpeg", "DOWN");
                health.put("ffmpegExitCode", exitCode);
            }
        } catch (Exception e) {
            health.put("ffmpeg", "DOWN");
            health.put("ffmpegError", e.getMessage());
        }

        return health;
    }
}