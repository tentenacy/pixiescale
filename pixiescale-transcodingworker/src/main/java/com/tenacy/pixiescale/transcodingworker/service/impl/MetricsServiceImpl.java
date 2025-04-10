package com.tenacy.pixiescale.transcodingworker.service.impl;

import com.tenacy.pixiescale.common.domain.TranscodingTask;
import com.tenacy.pixiescale.transcodingworker.config.MetricsConfig;
import com.tenacy.pixiescale.transcodingworker.service.MetricsService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class MetricsServiceImpl implements MetricsService {

    private final MetricsConfig metricsConfig;
    private final Counter transcodingTasksCounter;
    private final Counter transcodingTasksFailedCounter;
    private final Timer transcodingDurationTimer;
    private final Timer transcodingHighResolutionTimer;
    private final Timer transcodingMediumResolutionTimer;
    private final Timer transcodingLowResolutionTimer;

    @Override
    public void taskReceived(TranscodingTask task) {
        metricsConfig.incrementPendingJobs();
    }

    @Override
    public void taskStarted(TranscodingTask task) {
        metricsConfig.decrementPendingJobs();
        metricsConfig.incrementActiveJobs();
    }

    @Override
    public void taskCompleted(TranscodingTask task, Duration duration) {
        metricsConfig.decrementActiveJobs();
        transcodingTasksCounter.increment();
        transcodingDurationTimer.record(duration.toMillis(), TimeUnit.MILLISECONDS);

        // 해상도별 타이머 기록 (높이 기준으로 구분)
        if (task.getTargetHeight() != null) {
            int height = task.getTargetHeight();
            if (height >= 1080) {
                transcodingHighResolutionTimer.record(duration.toMillis(), TimeUnit.MILLISECONDS);
            } else if (height >= 480) {
                transcodingMediumResolutionTimer.record(duration.toMillis(), TimeUnit.MILLISECONDS);
            } else {
                transcodingLowResolutionTimer.record(duration.toMillis(), TimeUnit.MILLISECONDS);
            }
        }
    }

    @Override
    public void taskFailed(TranscodingTask task) {
        metricsConfig.decrementActiveJobs();
        transcodingTasksFailedCounter.increment();
    }

    @Override
    public void updatePendingJobsCount(int count) {
        metricsConfig.setPendingJobsCount(count);
    }
}