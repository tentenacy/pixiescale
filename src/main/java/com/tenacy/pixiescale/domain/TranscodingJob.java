package com.tenacy.pixiescale.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
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
}