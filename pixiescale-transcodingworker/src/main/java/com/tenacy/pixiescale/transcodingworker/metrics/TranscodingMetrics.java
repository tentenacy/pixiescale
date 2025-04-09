package com.tenacy.pixiescale.transcodingworker.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

@Component
public class TranscodingMetrics {
    private final Counter completedJobsCounter;
    private final Counter failedJobsCounter;
    private final Timer transcodingTimer;
    private final MeterRegistry registry;

    public TranscodingMetrics(MeterRegistry registry) {
        this.registry = registry;

        this.completedJobsCounter = Counter.builder("transcoding_jobs_completed_total")
                .description("완료된 트랜스코딩 작업 수")
                .register(registry);

        this.failedJobsCounter = Counter.builder("transcoding_jobs_failed_total")
                .description("실패한 트랜스코딩 작업 수")
                .register(registry);

        this.transcodingTimer = Timer.builder("transcoding_processing_seconds")
                .description("트랜스코딩 처리 시간 (초)")
                .register(registry);
    }

    public void recordCompletedJob() {
        completedJobsCounter.increment();
    }

    public void recordFailedJob() {
        failedJobsCounter.increment();
    }

    public Timer.Sample startTimerSample() {
        return Timer.start();
    }

    public void stopTimerSample(Timer.Sample sample) {
        sample.stop(transcodingTimer);
    }
}