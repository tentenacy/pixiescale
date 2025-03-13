package com.tenacy.pixiescale.mediatranscoding.api.dto;

import com.tenacy.pixiescale.mediatranscoding.domain.TranscodingConfig;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TranscodingJobRequest {
    private String mediaFileId;
    private TranscodingConfig config;
}