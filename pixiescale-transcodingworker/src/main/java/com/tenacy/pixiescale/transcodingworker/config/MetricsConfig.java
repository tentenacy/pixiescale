package com.tenacy.pixiescale.transcodingworker.config;

import io.micrometer.core.aop.TimedAspect;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.atomic.AtomicInteger;

@Configuration
public class MetricsConfig {

    @Bean
    public TimedAspect timedAspect(MeterRegistry registry) {
        return new TimedAspect(registry);
    }

    private final AtomicInteger pendingJobsCount = new AtomicInteger(0);
    private final AtomicInteger activeJobsCount = new AtomicInteger(0);

    @Bean
    public Counter transcodingTasksCounter(MeterRegistry registry) {
        return Counter.builder("pixiescale.transcoding.tasks.total")
                .description("트랜스코딩 작업 수")
                .tag("status", "completed")
                .register(registry);
    }

    @Bean
    public Counter transcodingTasksFailedCounter(MeterRegistry registry) {
        return Counter.builder("pixiescale.transcoding.tasks.total")
                .description("실패한 트랜스코딩 작업 수")
                .tag("status", "failed")
                .register(registry);
    }

    @Bean
    public Timer transcodingDurationTimer(MeterRegistry registry) {
        return Timer.builder("pixiescale.transcoding.duration.seconds")
                .description("트랜스코딩 작업 처리 시간")
                .tag("resolution", "all")
                .register(registry);
    }

    @Bean
    public Timer transcodingHighResolutionTimer(MeterRegistry registry) {
        return Timer.builder("pixiescale.transcoding.duration.seconds")
                .description("고해상도 트랜스코딩 작업 처리 시간")
                .tag("resolution", "high")
                .register(registry);
    }

    @Bean
    public Timer transcodingMediumResolutionTimer(MeterRegistry registry) {
        return Timer.builder("pixiescale.transcoding.duration.seconds")
                .description("중해상도 트랜스코딩 작업 처리 시간")
                .tag("resolution", "medium")
                .register(registry);
    }

    @Bean
    public Timer transcodingLowResolutionTimer(MeterRegistry registry) {
        return Timer.builder("pixiescale.transcoding.duration.seconds")
                .description("저해상도 트랜스코딩 작업 처리 시간")
                .tag("resolution", "low")
                .register(registry);
    }

    @Bean
    public Gauge  registerPendingJobsGauge(MeterRegistry registry) {
        return Gauge.builder("pixiescale.pending.jobs.count", pendingJobsCount, AtomicInteger::get)
                .description("대기 중인 트랜스코딩 작업 수")
                .register(registry);
    }

    @Bean
    public Gauge  registerActiveJobsGauge(MeterRegistry registry) {
        return Gauge.builder("pixiescale.active.jobs.count", activeJobsCount, AtomicInteger::get)
                .description("현재 처리 중인 트랜스코딩 작업 수")
                .register(registry);
    }

    public void incrementPendingJobs() {
        pendingJobsCount.incrementAndGet();
    }

    public void decrementPendingJobs() {
        pendingJobsCount.decrementAndGet();
    }

    public void incrementActiveJobs() {
        activeJobsCount.incrementAndGet();
    }

    public void decrementActiveJobs() {
        activeJobsCount.decrementAndGet();
    }

    public void setPendingJobsCount(int count) {
        pendingJobsCount.set(count);
    }
}