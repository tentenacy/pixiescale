package com.tenacy.pixiescale.common.event;

import com.tenacy.pixiescale.common.domain.MediaMetadata;
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