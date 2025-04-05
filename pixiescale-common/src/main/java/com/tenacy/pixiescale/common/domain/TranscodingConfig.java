package com.tenacy.pixiescale.common.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TranscodingConfig {
    private String targetCodec; // H.264, H.265, VP9 등
    private String targetFormat; // MP4, WebM 등
    private List<ResolutionPreset> resolutions;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ResolutionPreset {
        private String name; // 1080p, 720p, 480p, 360p 등
        private int width;
        private int height;
        private int bitrate; // kbps
    }
}