package com.tenacy.pixiescale.transcodingworker.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskResultEvent {
    private String taskId;
    private String jobId;
    private String status; // TranscodingTask.TaskStatus의 이름
    private String outputPath;
    private LocalDateTime completedAt;
    private String errorMessage;
}