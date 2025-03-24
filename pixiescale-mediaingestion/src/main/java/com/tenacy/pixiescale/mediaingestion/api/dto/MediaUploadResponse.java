package com.tenacy.pixiescale.mediaingestion.api.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MediaUploadResponse {
    private String mediaId;
    private String fileName;
}