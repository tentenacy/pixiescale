package com.tenacy.pixiescale.jobmanagement.event;

import com.tenacy.pixiescale.jobmanagement.domain.MediaFile;
import com.tenacy.pixiescale.jobmanagement.domain.MediaMetadata;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
@RequiredArgsConstructor
public class MediaEventListener {

    // 미디어 메타데이터 캐시 (업로드된 미디어 정보를 저장)
    private final ConcurrentHashMap<String, MediaFile> mediaCache = new ConcurrentHashMap<>();

    @KafkaListener(topics = "${app.kafka.topics.media-uploaded}", groupId = "${spring.kafka.consumer.group-id}")
    public void handleMediaUploaded(MediaUploadedEvent event) {
        log.info("미디어 업로드 이벤트 수신: {}", event.getMediaId());

        // 이벤트에서 MediaMetadata 객체 생성
        MediaMetadata metadata = null;
        if (event.getMetadata() != null) {
            metadata = MediaMetadata.builder()
                    .format(event.getMetadata().getFormat())
                    .width(event.getMetadata().getWidth())
                    .height(event.getMetadata().getHeight())
                    .codec(event.getMetadata().getCodec())
                    .duration(event.getMetadata().getDuration())
                    .bitrate(event.getMetadata().getBitrate())
                    .frameRate(event.getMetadata().getFrameRate())
                    .build();
        }

        // 미디어 파일 정보 캐싱
        MediaFile mediaFile = MediaFile.builder()
                .id(event.getMediaId())
                .fileName(event.getFileName())
                .contentType(event.getContentType())
                .fileSize(event.getFileSize())
                .storagePath(event.getStoragePath())
                .metadata(metadata)
                .uploadedAt(java.time.LocalDateTime.now())
                .build();

        mediaCache.put(event.getMediaId(), mediaFile);

        log.info("미디어 정보 캐싱 완료: {}", event.getMediaId());
    }

    // 캐시에서 미디어 정보 조회 메서드 추가
    public MediaFile getMediaInfo(String mediaId) {
        return mediaCache.get(mediaId);
    }
}