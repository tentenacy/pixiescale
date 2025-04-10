package com.tenacy.pixiescale.transcodingworker.service;

import com.tenacy.pixiescale.common.domain.TranscodingTask;

import java.time.Duration;

public interface MetricsService {
    void taskReceived(TranscodingTask task);
    void taskStarted(TranscodingTask task);
    void taskCompleted(TranscodingTask task, Duration duration);
    void taskFailed(TranscodingTask task);
    void updatePendingJobsCount(int count);
}
