package com.tenacy.pixiescale.jobmanagement.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class StorageResultEvent {
    private String taskId;
    private String jobId;
    private String storagePath;
    private String contentType;
    private boolean success;
    private String errorMessage;
}