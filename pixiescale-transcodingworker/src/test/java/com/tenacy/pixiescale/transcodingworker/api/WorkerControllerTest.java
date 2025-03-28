package com.tenacy.pixiescale.transcodingworker.api;

import com.tenacy.pixiescale.transcodingworker.config.FFmpegConfig;
import com.tenacy.pixiescale.transcodingworker.service.HealthCheckService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.util.HashMap;
import java.util.Map;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class WorkerControllerTest {

    @Mock
    private FFmpegConfig ffmpegConfig;

    @Mock
    private HealthCheckService healthCheckService;

    @InjectMocks
    private WorkerController workerController;

    @Test
    void getWorkerStatusShouldReturnWorkerInfo() {
        // Arrange
        WebTestClient webTestClient = WebTestClient.bindToController(workerController).build();

        when(ffmpegConfig.getBinaryPath()).thenReturn("ffmpeg");
        when(ffmpegConfig.isGpuAcceleration()).thenReturn(false);
        when(ffmpegConfig.getGpuDevice()).thenReturn("0");
        when(ffmpegConfig.getTimeoutSeconds()).thenReturn(3600);

        // Act & Assert
        webTestClient.get()
                .uri("/api/v1/transcoding/worker/status")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.workerId").isNotEmpty()
                .jsonPath("$.memory").isNotEmpty()
                .jsonPath("$.system").isNotEmpty()
                .jsonPath("$.ffmpeg.binaryPath").isEqualTo("ffmpeg")
                .jsonPath("$.ffmpeg.gpuAcceleration").isEqualTo(false);
    }

    @Test
    void getHealthShouldReturnHealthInfoWhenFFmpegAvailable() {
        // Arrange
        WebTestClient webTestClient = WebTestClient.bindToController(workerController).build();

        when(ffmpegConfig.getBinaryPath()).thenReturn("ffmpeg");

        Map<String, Object> ffmpegHealth = new HashMap<>();
        ffmpegHealth.put("ffmpeg", "UP");
        when(healthCheckService.checkFFmpegHealth(anyString())).thenReturn(ffmpegHealth);

        // Act & Assert
        webTestClient.get()
                .uri("/api/v1/transcoding/worker/health")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.status").isEqualTo("UP")
                .jsonPath("$.time").isNotEmpty()
                .jsonPath("$.ffmpeg").isEqualTo("UP");
    }

    @Test
    void getHealthShouldReturnDownStatusWhenFFmpegUnavailable() {
        // Arrange
        WebTestClient webTestClient = WebTestClient.bindToController(workerController).build();

        when(ffmpegConfig.getBinaryPath()).thenReturn("ffmpeg");

        Map<String, Object> ffmpegHealth = new HashMap<>();
        ffmpegHealth.put("ffmpeg", "DOWN");
        ffmpegHealth.put("ffmpegExitCode", 1);
        when(healthCheckService.checkFFmpegHealth(anyString())).thenReturn(ffmpegHealth);

        // Act & Assert
        webTestClient.get()
                .uri("/api/v1/transcoding/worker/health")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.status").isEqualTo("UP")
                .jsonPath("$.time").isNotEmpty()
                .jsonPath("$.ffmpeg").isEqualTo("DOWN")
                .jsonPath("$.ffmpegExitCode").isEqualTo(1);
    }
}