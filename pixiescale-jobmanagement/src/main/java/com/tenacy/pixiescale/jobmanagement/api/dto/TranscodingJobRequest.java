package com.tenacy.pixiescale.jobmanagement.api.dto;

import com.tenacy.pixiescale.jobmanagement.domain.TranscodingConfig;
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