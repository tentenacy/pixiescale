package com.tenacy.pixiescale.common.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TranscodingTaskEvent {
    private String taskId;
    private String jobId;
    private String targetFormat;
    private Integer targetWidth;
    private Integer targetHeight;
    private Integer targetBitrate;
}