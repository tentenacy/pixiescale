package com.tenacy.pixiescale.mediaingestion.service;

import com.tenacy.pixiescale.mediaingestion.config.StorageConfig;
import com.tenacy.pixiescale.common.domain.MediaFile;
import com.tenacy.pixiescale.common.domain.MediaMetadata;
import com.tenacy.pixiescale.common.event.MediaUploadedEvent;
import com.tenacy.pixiescale.mediaingestion.service.impl.MediaServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.mock.web.MockMultipartFile;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class MediaServiceTest {

    @Mock
    private StorageService storageService;

    @Mock
    private MetadataExtractor metadataExtractor;

    @Mock
    private EventPublisher eventPublisher;

    @Mock
    private StorageConfig storageConfig;

    private MediaService mediaService;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() throws IOException {
        // 임시 파일 생성
        Files.write(tempDir.resolve("test-file.mp4"), "test content".getBytes());

        // 모든 mock을 lenient 모드로 설정
        // lenient 모드는 사용되지 않는 stubbing을 허용합니다
        lenient().when(storageConfig.getBaseDir()).thenReturn(tempDir.toString());
        lenient().when(eventPublisher.publishMediaUploaded(any())).thenReturn(Mono.empty());
        lenient().when(storageService.store(any(MockMultipartFile.class), anyString())).thenReturn(Mono.just("test-file.mp4"));

        // 메타데이터 추출 모킹
        MediaMetadata metadata = MediaMetadata.builder()
                .format("mp4")
                .width(1920)
                .height(1080)
                .codec("h264")
                .duration(120.0)
                .bitrate(5000)
                .frameRate(30.0)
                .build();

        lenient().when(metadataExtractor.extractMetadata(any(Path.class))).thenReturn(Mono.just(metadata));

        // 서비스 생성
        mediaService = new MediaServiceImpl(
                storageConfig,
                storageService,
                metadataExtractor,
                eventPublisher
        );
    }

    @Test
    void storeMediaShouldSaveFileAndPublishEvent() {
        // Arrange
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "test.mp4",
                "video/mp4",
                "test content".getBytes()
        );

        // Act & Assert
        StepVerifier.create(mediaService.storeMedia(file))
                .assertNext(mediaFile -> {
                    assertNotNull(mediaFile);
                    assertNotNull(mediaFile.getId());
                    assertEquals("test.mp4", mediaFile.getFileName());
                    assertEquals("video/mp4", mediaFile.getContentType());
                    assertEquals(12L, mediaFile.getFileSize());
                    assertEquals("test-file.mp4", mediaFile.getStoragePath());
                    assertNotNull(mediaFile.getMetadata());
                    assertEquals("mp4", mediaFile.getMetadata().getFormat());
                    assertEquals(1920, mediaFile.getMetadata().getWidth());
                    assertEquals(1080, mediaFile.getMetadata().getHeight());
                })
                .verifyComplete();

        // Verify interactions
        verify(storageService).store(any(MockMultipartFile.class), anyString());
        verify(metadataExtractor).extractMetadata(any(Path.class));

        ArgumentCaptor<MediaUploadedEvent> eventCaptor = ArgumentCaptor.forClass(MediaUploadedEvent.class);
        verify(eventPublisher).publishMediaUploaded(eventCaptor.capture());

        MediaUploadedEvent capturedEvent = eventCaptor.getValue();
        assertNotNull(capturedEvent);
        assertEquals("test.mp4", capturedEvent.getFileName());
        assertEquals("video/mp4", capturedEvent.getContentType());
    }

    @Test
    void getMediaResourceShouldReturnResource() throws Exception {
        // Arrange
        String mediaId = "test-media-id";
        Resource mockResource = new FileSystemResource(tempDir.resolve("test-file.mp4").toFile());

        MediaFile mediaFile = MediaFile.builder()
                .id(mediaId)
                .fileName("test.mp4")
                .storagePath("test-file.mp4")
                .build();

        // 테스트에 필요한 mock만 설정
        when(storageService.loadAsResource("test-file.mp4")).thenReturn(Mono.just(mockResource));

        // mediaFileStore에 직접 주입
        ConcurrentHashMap<String, MediaFile> store = new ConcurrentHashMap<>();
        store.put(mediaId, mediaFile);

        java.lang.reflect.Field field = MediaServiceImpl.class.getDeclaredField("mediaFileStore");
        field.setAccessible(true);
        field.set(mediaService, store);

        // Act & Assert
        StepVerifier.create(mediaService.getMediaResource(mediaId))
                .expectNextMatches(resource -> {
                    try {
                        return resource.contentLength() > 0 &&
                                "test-file.mp4".equals(resource.getFile().getName());
                    } catch (IOException e) {
                        return false;
                    }
                })
                .verifyComplete();

        verify(storageService).loadAsResource("test-file.mp4");
    }

    @Test
    void getMediaInfoShouldReturnMediaFile() throws Exception {
        // Arrange
        String mediaId = "test-media-id";
        MediaFile mediaFile = MediaFile.builder()
                .id(mediaId)
                .fileName("test.mp4")
                .contentType("video/mp4")
                .fileSize(1024L)
                .storagePath("test-path.mp4")
                .metadata(MediaMetadata.builder().build())
                .build();

        // mediaFileStore에 직접 주입
        ConcurrentHashMap<String, MediaFile> store = new ConcurrentHashMap<>();
        store.put(mediaId, mediaFile);

        java.lang.reflect.Field field = MediaServiceImpl.class.getDeclaredField("mediaFileStore");
        field.setAccessible(true);
        field.set(mediaService, store);

        // Act & Assert
        StepVerifier.create(mediaService.getMediaInfo(mediaId))
                .expectNext(mediaFile)
                .verifyComplete();
    }

    @Test
    void deleteMediaShouldRemoveFromStoreAndDeleteFile() throws Exception {
        // Arrange
        String mediaId = "test-media-id";
        MediaFile mediaFile = MediaFile.builder()
                .id(mediaId)
                .fileName("test.mp4")
                .storagePath("test-file.mp4")
                .build();

        when(storageService.delete("test-file.mp4")).thenReturn(Mono.empty());

        // mediaFileStore에 직접 주입
        ConcurrentHashMap<String, MediaFile> store = new ConcurrentHashMap<>();
        store.put(mediaId, mediaFile);

        java.lang.reflect.Field field = MediaServiceImpl.class.getDeclaredField("mediaFileStore");
        field.setAccessible(true);
        field.set(mediaService, store);

        // Act & Assert
        StepVerifier.create(mediaService.deleteMedia(mediaId))
                .verifyComplete();

        // Verify storage service was called to delete file
        verify(storageService).delete("test-file.mp4");

        // Verify file was removed from store
        ConcurrentHashMap<String, MediaFile> storeAfterDelete =
                (ConcurrentHashMap<String, MediaFile>) field.get(mediaService);
        assertEquals(0, storeAfterDelete.size());
    }
}