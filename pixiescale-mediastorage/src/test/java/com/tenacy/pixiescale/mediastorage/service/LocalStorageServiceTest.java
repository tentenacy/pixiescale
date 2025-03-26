package com.tenacy.pixiescale.mediastorage.service;

import com.tenacy.pixiescale.mediastorage.config.StorageConfig;
import com.tenacy.pixiescale.mediastorage.service.impl.LocalStorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.Resource;
import org.springframework.mock.web.MockMultipartFile;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class LocalStorageServiceTest {

    @Mock
    private StorageConfig storageConfig;

    private StorageService storageService;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        when(storageConfig.getBaseDir()).thenReturn(tempDir.toString());
        storageService = new LocalStorageService(storageConfig);
    }

    @Test
    void storeShouldSaveMultipartFile() {
        // Arrange
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "test.mp4",
                "video/mp4",
                "test content".getBytes()
        );

        // Act & Assert
        StepVerifier.create(storageService.store(file, "test-file.mp4"))
                .assertNext(filename -> {
                    assertEquals("test-file.mp4", filename);
                    assertTrue(Files.exists(tempDir.resolve("test-file.mp4")));
                })
                .verifyComplete();
    }

    @Test
    void storeShouldSaveFileFromPath() throws IOException {
        // Arrange
        Path sourcePath = tempDir.resolve("source-file.mp4");
        Files.write(sourcePath, "test content".getBytes());

        // Act & Assert
        StepVerifier.create(storageService.store(sourcePath, "test-file.mp4"))
                .assertNext(filename -> {
                    assertEquals("test-file.mp4", filename);
                    assertTrue(Files.exists(tempDir.resolve("test-file.mp4")));
                })
                .verifyComplete();
    }

    @Test
    void storeShouldSaveByteArray() {
        // Arrange
        byte[] data = "test content".getBytes();

        // Act & Assert
        StepVerifier.create(storageService.store(data, "test-file.mp4", "video/mp4"))
                .assertNext(filename -> {
                    assertEquals("test-file.mp4", filename);
                    assertTrue(Files.exists(tempDir.resolve("test-file.mp4")));
                })
                .verifyComplete();
    }

    @Test
    void loadAsResourceShouldReturnResource() throws IOException {
        // Arrange
        Path filePath = tempDir.resolve("test-file.mp4");
        Files.write(filePath, "test content".getBytes());

        // Act & Assert
        StepVerifier.create(storageService.loadAsResource("test-file.mp4"))
                .assertNext(resource -> {
                    assertNotNull(resource);
                    assertTrue(resource.exists());
                    assertEquals("test-file.mp4", resource.getFilename());
                    try {
                        assertEquals(12L, resource.contentLength());
                    } catch (IOException e) {
                        fail("리소스 콘텐츠 길이 읽기 실패", e);
                    }
                })
                .verifyComplete();
    }

    @Test
    void deleteShouldRemoveFile() throws IOException {
        // Arrange
        Path filePath = tempDir.resolve("test-file.mp4");
        Files.write(filePath, "test content".getBytes());

        // Act & Assert
        StepVerifier.create(storageService.delete("test-file.mp4"))
                .verifyComplete();

        assertFalse(Files.exists(filePath));
    }
}