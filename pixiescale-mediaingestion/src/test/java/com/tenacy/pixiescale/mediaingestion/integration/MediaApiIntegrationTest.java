package com.tenacy.pixiescale.mediaingestion.integration;

import com.tenacy.pixiescale.mediaingestion.config.TestConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.BodyInserters;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@SpringBootTest
@AutoConfigureWebTestClient
@ActiveProfiles("test")
@Import(TestConfig.class)
public class MediaApiIntegrationTest {

    @Autowired
    private WebTestClient webTestClient;

    @TempDir
    Path tempDir;

    @Test
    void fullMediaLifecycle() throws IOException {
        // 1. 테스트 파일 생성 - 실제 MP4 헤더를 포함한 내용을 생성
        Path testFilePath = tempDir.resolve("test-video.mp4");

        // 간단한 MP4 파일 대신 더 나은 방법으로 내용 생성
        // 여기서는 간단한 바이트 배열을 사용
        byte[] videoBytes = new byte[1024];
        // MP4 헤더를 시뮬레이션 (실제 MP4 헤더와 유사하지만 완전하지는 않음)
        videoBytes[0] = 0x00;
        videoBytes[1] = 0x00;
        videoBytes[2] = 0x00;
        videoBytes[3] = 0x18; // box size
        videoBytes[4] = 0x66; // 'f'
        videoBytes[5] = 0x74; // 't'
        videoBytes[6] = 0x79; // 'y'
        videoBytes[7] = 0x70; // 'p'

        Files.write(testFilePath, videoBytes);

        // 2. 파일 업로드
        MultipartBodyBuilder bodyBuilder = new MultipartBodyBuilder();
        bodyBuilder.part("file", new FileSystemResource(testFilePath.toFile()))
                .header("Content-Disposition", "form-data; name=file; filename=test-video.mp4")
                .contentType(MediaType.APPLICATION_OCTET_STREAM);

        // 3. 테스트 실행
        String responseBody = webTestClient.post()
                .uri("/api/v1/media")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(BodyInserters.fromMultipartData(bodyBuilder.build()))
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .returnResult()
                .getResponseBody();

        // 응답에서 mediaId 추출
        String mediaId = extractMediaId(responseBody);

        // 4. 나머지 테스트 (진행 가능한 경우에만)
        if (mediaId != null) {
            // 미디어 정보 조회
            webTestClient.get()
                    .uri("/api/v1/media/{mediaId}/info", mediaId)
                    .exchange()
                    .expectStatus().isOk();

            // 미디어 다운로드
            webTestClient.get()
                    .uri("/api/v1/media/{mediaId}", mediaId)
                    .exchange()
                    .expectStatus().isOk();

            // 미디어 삭제
            webTestClient.delete()
                    .uri("/api/v1/media/{mediaId}", mediaId)
                    .exchange()
                    .expectStatus().isNoContent();
        }
    }

    // 응답에서 mediaId 추출하는 헬퍼 메서드
    private String extractMediaId(String responseBody) {
        if (responseBody == null || responseBody.isEmpty()) {
            return "test-media-id"; // 기본값
        }

        // JSON 파싱 (간단한 방식)
        if (responseBody.contains("\"mediaId\":")) {
            String[] parts = responseBody.split("\"mediaId\":");
            if (parts.length > 1) {
                String idPart = parts[1].trim();
                if (idPart.startsWith("\"")) {
                    int endQuote = idPart.indexOf("\"", 1);
                    if (endQuote > 0) {
                        return idPart.substring(1, endQuote);
                    }
                }
            }
        }

        return "test-media-id"; // 추출 실패 시 기본값
    }
}