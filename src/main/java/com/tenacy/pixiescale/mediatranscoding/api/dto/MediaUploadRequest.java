package com.tenacy.pixiescale.mediatranscoding.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MediaUploadRequest {
    private String fileName;
    private String contentType;
}