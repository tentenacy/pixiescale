package com.tenacy.pixiescale.jobmanagement.api.dto;

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
public class TranscodingJobResponse {
    private String id;
    private String mediaFileId;
    private TranscodingJob.JobStatus status;
    private int totalTasks;
    private int completedTasks;
    private LocalDateTime createdAt;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
}