package com.tenacy.pixiescale.mediastorage.api;

import com.tenacy.pixiescale.mediastorage.service.StorageService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class StorageControllerTest {

    @Mock
    private StorageService storageService;

    @InjectMocks
    private StorageController storageController;

    @Test
    void getFileShouldReturnResource() {
        // Arrange
        WebTestClient webTestClient = WebTestClient.bindToController(storageController).build();

        String filename = "test.mp4";
        Resource mockResource = new ByteArrayResource("test content".getBytes()) {
            @Override
            public String getFilename() {
                return filename;
            }
        };

        when(storageService.loadAsResource(filename)).thenReturn(Mono.just(mockResource));

        // Act & Assert
        webTestClient.get()
                .uri("/api/v1/storage/{filename}", filename)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.parseMediaType("video/mp4"))
                .expectHeader().valueMatches("Content-Disposition", "attachment; filename=\"test.mp4\"")
                .expectBody()
                .consumeWith(response -> {
                    byte[] responseBody = response.getResponseBody();
                    assert responseBody != null;
                    assert responseBody.length == 12;
                });
    }

    @Test
    void deleteFileShouldReturnNoContent() {
        // Arrange
        WebTestClient webTestClient = WebTestClient.bindToController(storageController).build();

        String filename = "test.mp4";
        when(storageService.delete(filename)).thenReturn(Mono.empty());

        // Act & Assert
        webTestClient.delete()
                .uri("/api/v1/storage/{filename}", filename)
                .exchange()
                .expectStatus().isNoContent();

        verify(storageService).delete(filename);
    }
}