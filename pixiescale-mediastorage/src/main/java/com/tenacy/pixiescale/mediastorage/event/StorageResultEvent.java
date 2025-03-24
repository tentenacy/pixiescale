package com.tenacy.pixiescale.mediastorage.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
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