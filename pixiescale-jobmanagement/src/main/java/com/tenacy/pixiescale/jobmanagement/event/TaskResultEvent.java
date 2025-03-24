package com.tenacy.pixiescale.jobmanagement.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TaskResultEvent {
    private String taskId;
    private String jobId;
    private String status;
    private String outputPath;
    private LocalDateTime completedAt;
    private String errorMessage;
}