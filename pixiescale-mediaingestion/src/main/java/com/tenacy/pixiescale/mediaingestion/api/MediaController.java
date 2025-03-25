package com.tenacy.pixiescale.mediaingestion.api;

import com.tenacy.pixiescale.mediaingestion.api.dto.MediaUploadResponse;
import com.tenacy.pixiescale.mediaingestion.domain.MediaFile;
import com.tenacy.pixiescale.mediaingestion.service.MediaService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.http.codec.multipart.Part;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/media")
@RequiredArgsConstructor
public class MediaController {

    private final MediaService mediaService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Mono<ResponseEntity<MediaUploadResponse>> uploadMedia(
            @RequestPart("file") Mono<FilePart> filePart) {

        return filePart
                .flatMap(part -> {
                    String tempDir = System.getProperty("java.io.tmpdir");
                    String tempFileName = part.filename();
                    Path tempPath = Path.of(tempDir, tempFileName);
                    File tempFile = tempPath.toFile();

                    // 파일 전송 후 처리 로직
                    return part.transferTo(tempFile)
                            .then(Mono.fromCallable(() -> {
                                try {
                                    // CustomMultipartFile 생성
                                    CustomMultipartFile multipartFile = new CustomMultipartFile(
                                            tempFile,
                                            part.filename(),
                                            part.headers().getContentType() != null
                                                    ? part.headers().getContentType().toString()
                                                    : MediaType.APPLICATION_OCTET_STREAM_VALUE);

                                    return multipartFile;
                                } catch (Exception e) {
                                    if (tempFile.exists()) {
                                        tempFile.delete();
                                    }
                                    throw new RuntimeException("파일 처리 실패: " + e.getMessage(), e);
                                }
                            }))
                            .flatMap(multipartFile -> mediaService.storeMedia(multipartFile)
                                    .map(mediaFile -> {
                                        // 임시 파일 삭제
                                        if (tempFile.exists()) {
                                            tempFile.delete();
                                        }
                                        return ResponseEntity.ok(
                                                new MediaUploadResponse(mediaFile.getId(), mediaFile.getFileName())
                                        );
                                    })
                            )
                            .onErrorResume(e -> {
                                // 오류 처리
                                log.error("파일 업로드 중 오류 발생: {}", e.getMessage(), e);
                                // 임시 파일 삭제
                                if (tempFile.exists()) {
                                    tempFile.delete();
                                }
                                return Mono.error(e);
                            });
                });
    }

    @GetMapping("/{mediaId}")
    public Mono<ResponseEntity<Resource>> getMedia(@PathVariable String mediaId) {
        return mediaService.getMediaResource(mediaId)
                .map(resource -> {
                    String contentType = determineContentType(resource);
                    return ResponseEntity.ok()
                            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + resource.getFilename() + "\"")
                            .contentType(MediaType.parseMediaType(contentType))
                            .body(resource);
                });
    }

    @GetMapping("/{mediaId}/info")
    public Mono<ResponseEntity<MediaFile>> getMediaInfo(@PathVariable String mediaId) {
        return mediaService.getMediaInfo(mediaId)
                .map(ResponseEntity::ok);
    }

    @DeleteMapping("/{mediaId}")
    public Mono<ResponseEntity<Void>> deleteMedia(@PathVariable String mediaId) {
        return mediaService.deleteMedia(mediaId)
                .then(Mono.just(ResponseEntity.noContent().<Void>build()));
    }

    private String determineContentType(Resource resource) {
        // 파일 이름으로 콘텐츠 타입 추정
        String fileName = resource.getFilename();
        if (fileName != null) {
            String extension = fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase();
            switch (extension) {
                case "mp4":
                    return "video/mp4";
                case "webm":
                    return "video/webm";
                case "mkv":
                    return "video/x-matroska";
                case "avi":
                    return "video/x-msvideo";
                case "mov":
                    return "video/quicktime";
                case "wmv":
                    return "video/x-ms-wmv";
                case "flv":
                    return "video/x-flv";
                case "m4v":
                    return "video/x-m4v";
                case "m3u8":
                    return "application/x-mpegURL";
                case "ts":
                    return "video/MP2T";
                case "3gp":
                    return "video/3gpp";
                case "mpg":
                case "mpeg":
                    return "video/mpeg";
            }
        }

        // 기본 콘텐츠 타입
        return MediaType.APPLICATION_OCTET_STREAM_VALUE;
    }

    /**
     * CustomMultipartFile 클래스 - Spring WebFlux와 MediaService 간 호환성을 위한 어댑터
     */
    private static class CustomMultipartFile implements org.springframework.web.multipart.MultipartFile {
        private final File file;
        private final String name;
        private final String contentType;

        public CustomMultipartFile(File file, String name, String contentType) {
            this.file = file;
            this.name = name;
            this.contentType = contentType;
        }

        @Override
        public String getName() {
            return "file";
        }

        @Override
        public String getOriginalFilename() {
            return name;
        }

        @Override
        public String getContentType() {
            return contentType;
        }

        @Override
        public boolean isEmpty() {
            return file.length() == 0;
        }

        @Override
        public long getSize() {
            return file.length();
        }

        @Override
        public byte[] getBytes() throws IOException {
            return Files.readAllBytes(file.toPath());
        }

        @Override
        public java.io.InputStream getInputStream() throws IOException {
            return Files.newInputStream(file.toPath());
        }

        @Override
        public void transferTo(File dest) throws IOException, IllegalStateException {
            Files.copy(file.toPath(), dest.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
    }
}