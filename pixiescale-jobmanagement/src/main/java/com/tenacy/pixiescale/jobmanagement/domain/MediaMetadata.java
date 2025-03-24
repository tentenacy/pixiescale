package com.tenacy.pixiescale.jobmanagement.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MediaMetadata {
    private String format;
    private Integer width;
    private Integer height;
    private String codec;
    private Double duration;
    private Integer bitrate;
    private Double frameRate;
}