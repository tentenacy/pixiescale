package com.tenacy.pixiescale.transcodingworker.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TranscodingTask {
    private String id;
    private String jobId;
    private String targetFormat;
    private Integer targetWidth;
    private Integer targetHeight;
    private Integer targetBitrate;
    private TaskStatus status;
    private String outputPath;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private String errorMessage;

    public enum TaskStatus {
        PENDING, PROCESSING, COMPLETED, FAILED
    }
}