package com.tenacy.pixiescale.transcodingworker.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
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
