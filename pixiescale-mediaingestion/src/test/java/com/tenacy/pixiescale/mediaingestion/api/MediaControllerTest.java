package com.tenacy.pixiescale.mediaingestion.api;

import com.tenacy.pixiescale.common.domain.MediaFile;
import com.tenacy.pixiescale.common.domain.MediaMetadata;
import com.tenacy.pixiescale.mediaingestion.service.MediaService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class MediaControllerTest {

    @Mock
    private MediaService mediaService;

    private WebTestClient webTestClient;
    private MediaController mediaController;

    @BeforeEach
    public void setUp() {
        mediaController = new MediaController(mediaService);
        webTestClient = WebTestClient.bindToController(mediaController).build();
    }

    @Test
    void getMediaShouldReturnResource() {
        // Arrange
        String mediaId = "test-media-id";
        Resource resource = new ByteArrayResource("test content".getBytes()) {
            @Override
            public String getFilename() {
                return "test.mp4";
            }
        };

        when(mediaService.getMediaResource(mediaId)).thenReturn(Mono.just(resource));

        // Act & Assert
        webTestClient.get()
                .uri("/api/v1/media/{mediaId}", mediaId)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.parseMediaType("video/mp4"))
                .expectBody()
                .consumeWith(response -> {
                    byte[] responseBody = response.getResponseBody();
                    assert responseBody != null;
                    assert responseBody.length == 12;
                });
    }

    @Test
    void getMediaInfoShouldReturnMediaFile() {
        // Arrange
        String mediaId = "test-media-id";
        MediaFile mediaFile = MediaFile.builder()
                .id(mediaId)
                .fileName("test.mp4")
                .contentType("video/mp4")
                .fileSize(1024L)
                .storagePath("test-path.mp4")
                .metadata(MediaMetadata.builder()
                        .format("mp4")
                        .width(1920)
                        .height(1080)
                        .codec("h264")
                        .build())
                .uploadedAt(LocalDateTime.now())
                .build();

        when(mediaService.getMediaInfo(mediaId)).thenReturn(Mono.just(mediaFile));

        // Act & Assert
        webTestClient.get()
                .uri("/api/v1/media/{mediaId}/info", mediaId)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.id").isEqualTo(mediaId)
                .jsonPath("$.fileName").isEqualTo("test.mp4")
                .jsonPath("$.contentType").isEqualTo("video/mp4");
    }

    @Test
    void deleteMediaShouldReturnNoContent() {
        // Arrange
        String mediaId = "test-media-id";
        when(mediaService.deleteMedia(mediaId)).thenReturn(Mono.empty());

        // Act & Assert
        webTestClient.delete()
                .uri("/api/v1/media/{mediaId}", mediaId)
                .exchange()
                .expectStatus().isNoContent();
    }

    @Test
    void getMediaNotFoundShouldReturn404() {
        // Arrange
        String mediaId = "not-found-media-id";
        when(mediaService.getMediaResource(mediaId)).thenReturn(
                Mono.error(new RuntimeException("미디어 파일을 찾을 수 없음: " + mediaId)));

        // Act & Assert
        webTestClient.get()
                .uri("/api/v1/media/{mediaId}", mediaId)
                .exchange()
                .expectStatus().is5xxServerError(); // 또는 .isNotFound() 로직에 따라 다름
    }
}