package com.tenacy.pixiescale.mediatranscoding.domain;

import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
public class TranscodingJob {
    private String id;
    private String mediaFileId;
    private JobStatus status;
    private List<TranscodingTask> tasks = new ArrayList<>();
    private LocalDateTime createdAt;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private String errorMessage;

    public enum JobStatus {
        PENDING, PROCESSING, COMPLETED, FAILED
    }

    @Builder
    public TranscodingJob(String id, String mediaFileId, JobStatus status, List<TranscodingTask> tasks, LocalDateTime createdAt, LocalDateTime startedAt, LocalDateTime completedAt, String errorMessage) {
        this.id = id;
        this.mediaFileId = mediaFileId;
        this.status = status;
        if (tasks != null) {
            this.tasks = tasks;
        }
        this.createdAt = createdAt;
        this.startedAt = startedAt;
        this.completedAt = completedAt;
        this.errorMessage = errorMessage;
    }
}