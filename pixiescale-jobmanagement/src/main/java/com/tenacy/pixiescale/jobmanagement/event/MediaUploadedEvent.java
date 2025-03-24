package com.tenacy.pixiescale.jobmanagement.event;

import com.tenacy.pixiescale.jobmanagement.domain.MediaMetadata;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
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