package com.tenacy.pixiescale.common.event;

import com.tenacy.pixiescale.common.domain.TranscodingConfig;
import com.tenacy.pixiescale.common.domain.TranscodingJob;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TranscodingJobEvent {
    private String jobId;
    private String mediaFileId;
    private TranscodingJob.JobStatus status;
    private TranscodingConfig config;
    private LocalDateTime timestamp;
}