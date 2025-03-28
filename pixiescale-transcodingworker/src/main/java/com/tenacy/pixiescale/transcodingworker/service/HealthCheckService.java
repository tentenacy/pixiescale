package com.tenacy.pixiescale.transcodingworker.service;

import java.util.Map;

public interface HealthCheckService {
    Map<String, Object> checkFFmpegHealth(String ffmpegPath);
}
