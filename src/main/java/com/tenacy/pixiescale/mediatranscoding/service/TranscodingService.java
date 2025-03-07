package com.tenacy.pixiescale.mediatranscoding.service;

import com.tenacy.pixiescale.mediatranscoding.api.dto.TranscodingJobRequest;
import com.tenacy.pixiescale.mediatranscoding.domain.TranscodingJob;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface TranscodingService {
    Mono<TranscodingJob> createJob(TranscodingJobRequest request);
    Mono<TranscodingJob> getJob(String jobId);
    Flux<TranscodingJob> getJobsByMediaId(String mediaId);
    Mono<Void> cancelJob(String jobId);
}