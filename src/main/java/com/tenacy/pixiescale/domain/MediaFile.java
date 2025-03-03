package com.tenacy.pixiescale.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MediaFile {
    private String id;
    private String fileName;
    private String contentType;
    private long fileSize;
    private String storagePath;
    private MediaMetadata metadata;
    private LocalDateTime uploadedAt;
}