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
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Slf4j
@RestController
@RequestMapping("/api/v1/media")
@RequiredArgsConstructor
public class MediaController {

    private final MediaService mediaService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Mono<ResponseEntity<MediaUploadResponse>> uploadMedia(@RequestPart("file") Mono<FilePart> filePart) {
        return filePart
                .flatMap(part -> {
                    String tempDir = System.getProperty("java.io.tmpdir");
                    String tempFileName = part.filename();
                    Path tempPath = Path.of(tempDir, tempFileName);
                    File tempFile = tempPath.toFile();

                    // 파일 전송 후 처리 로직
                    return part.transferTo(tempFile)
                            .then(Mono.defer(() -> {
                                try {
                                    // FilePart를 MultipartFile로 변환
                                    FilePartToMultipartFile multipartFile = new FilePartToMultipartFile(
                                            tempFile, part.filename(),
                                            part.headers().getContentType() != null
                                                    ? part.headers().getContentType().toString()
                                                    : "application/octet-stream");

                                    // mediaService 호출 및 응답 생성
                                    return mediaService.storeMedia(multipartFile)
                                            .map(mediaFile -> ResponseEntity.ok(
                                                    new MediaUploadResponse(mediaFile.getId(), mediaFile.getFileName())
                                            ))
                                            .doFinally(signalType -> {
                                                // 임시 파일 삭제
                                                if (tempFile.exists()) {
                                                    tempFile.delete();
                                                }
                                            });
                                } catch (Exception e) {
                                    // 오류 발생 시 임시 파일 삭제 및 에러 전파
                                    if (tempFile.exists()) {
                                        tempFile.delete();
                                    }
                                    return Mono.error(new RuntimeException("파일 처리 실패", e));
                                }
                            }));
                });
    }

    @GetMapping("/{mediaId}")
    public Mono<ResponseEntity<Resource>> getMedia(@PathVariable String mediaId) {
        return mediaService.getMediaResource(mediaId)
                .flatMap(resource -> Mono.fromCallable(() -> {
                    String contentType = determineContentType(resource);
                    return ResponseEntity.ok()
                            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + resource.getFilename() + "\"")
                            .contentType(MediaType.parseMediaType(contentType))
                            .body(resource);
                }));
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
        try {
            return Files.probeContentType(Path.of(resource.getURI()));
        } catch (IOException e) {
            return MediaType.APPLICATION_OCTET_STREAM_VALUE;
        }
    }

    private static class FilePartToMultipartFile implements org.springframework.web.multipart.MultipartFile {
        private final File file;
        private final String name;
        private final String contentType;

        public FilePartToMultipartFile(File file, String name, String contentType) {
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
            Files.copy(file.toPath(), dest.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
    }
}