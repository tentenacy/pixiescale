package com.tenacy.pixiescale.transcodingworker.api;

import com.tenacy.pixiescale.transcodingworker.config.FFmpegConfig;
import com.tenacy.pixiescale.transcodingworker.service.HealthCheckService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.OperatingSystemMXBean;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/transcoding/worker")
@RequiredArgsConstructor
public class WorkerController {

    private final FFmpegConfig ffmpegConfig;
    private final HealthCheckService healthCheckService;

    @GetMapping("/status")
    public Mono<ResponseEntity<Map<String, Object>>> getWorkerStatus() {
        return Mono.fromCallable(() -> {
            Map<String, Object> status = new HashMap<>();

            // JVM 정보
            MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
            OperatingSystemMXBean osBean = ManagementFactory.getOperatingSystemMXBean();

            Map<String, Object> memory = new HashMap<>();
            memory.put("heapUsed", memoryBean.getHeapMemoryUsage().getUsed());
            memory.put("heapMax", memoryBean.getHeapMemoryUsage().getMax());
            memory.put("nonHeapUsed", memoryBean.getNonHeapMemoryUsage().getUsed());

            Map<String, Object> system = new HashMap<>();
            system.put("availableProcessors", osBean.getAvailableProcessors());
            system.put("systemLoadAverage", osBean.getSystemLoadAverage());

            Map<String, Object> ffmpeg = new HashMap<>();
            ffmpeg.put("binaryPath", ffmpegConfig.getBinaryPath());
            ffmpeg.put("gpuAcceleration", ffmpegConfig.isGpuAcceleration());
            ffmpeg.put("gpuDevice", ffmpegConfig.getGpuDevice());
            ffmpeg.put("timeoutSeconds", ffmpegConfig.getTimeoutSeconds());

            // 작업자 정보
            status.put("workerId", System.getProperty("worker.id", "worker-" + ManagementFactory.getRuntimeMXBean().getName()));
            status.put("memory", memory);
            status.put("system", system);
            status.put("ffmpeg", ffmpeg);
            status.put("startTime", ManagementFactory.getRuntimeMXBean().getStartTime());
            status.put("uptime", ManagementFactory.getRuntimeMXBean().getUptime());

            return ResponseEntity.ok(status);
        });
    }

    @GetMapping("/health")
    public Mono<ResponseEntity<Map<String, Object>>> getHealth() {
        return Mono.fromCallable(() -> {
            Map<String, Object> health = new HashMap<>();
            health.put("status", "UP");
            health.put("time", System.currentTimeMillis());

            // FFmpeg 상태 확인을 서비스에 위임
            Map<String, Object> ffmpegHealth = healthCheckService.checkFFmpegHealth(ffmpegConfig.getBinaryPath());
            health.putAll(ffmpegHealth);

            return ResponseEntity.ok(health);
        });
    }
}