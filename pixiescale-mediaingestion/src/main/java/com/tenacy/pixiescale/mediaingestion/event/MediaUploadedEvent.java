package com.tenacy.pixiescale.mediaingestion.event;

import com.tenacy.pixiescale.mediaingestion.domain.MediaMetadata;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MediaUploadedEvent {
    private String mediaId;
    private String fileName;
    private String contentType;
    private long fileSize;
    private String storagePath;
    private MediaMetadata metadata;
}