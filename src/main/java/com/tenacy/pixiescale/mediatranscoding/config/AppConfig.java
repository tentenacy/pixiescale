package com.tenacy.pixiescale.mediatranscoding.config;

import com.tenacy.pixiescale.mediatranscoding.service.TranscodingWorker;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;


@Slf4j
@Configuration
@RequiredArgsConstructor
public class AppConfig {

    private final TranscodingWorker transcodingWorker;

    @PostConstruct
    public void init() {
        log.info("애플리케이션 초기화 중...");
        transcodingWorker.initialize();
    }

    @PreDestroy
    public void shutdown() {
        log.info("애플리케이션 종료 중...");
        transcodingWorker.shutdown();
    }
}