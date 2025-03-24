package com.tenacy.pixiescale.jobmanagement.event;

import com.tenacy.pixiescale.jobmanagement.domain.TranscodingConfig;
import com.tenacy.pixiescale.jobmanagement.domain.TranscodingJob;
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