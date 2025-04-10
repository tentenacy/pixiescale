package com.tenacy.pixiescale.transcodingworker.config;

import com.tenacy.pixiescale.common.event.TranscodingTaskEvent;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.listener.ContainerProperties.AckMode;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

@Configuration
public class KafkaConfig {

    @Value("${app.transcoding.max-concurrent-tasks:2}")
    private int maxConcurrentTasks;

    // 트랜스코딩 작업을 위한 전용 스케줄러 생성
    @Bean
    public Scheduler transcodingScheduler() {
        return Schedulers.newBoundedElastic(
                maxConcurrentTasks,     // 최대 동시 작업 수
                100,                     // 큐 크기
                "transcoding-worker",   // 스레드 이름 접두사
                60                      // 유휴 스레드 유지 시간(초)
        );
    }

    // Kafka 리스너 컨테이너 팩토리 설정
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, TranscodingTaskEvent> kafkaListenerContainerFactory(
            ConsumerFactory<String, TranscodingTaskEvent> consumerFactory) {

        ConcurrentKafkaListenerContainerFactory<String, TranscodingTaskEvent> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);

        // 수동 오프셋 커밋 설정
        factory.getContainerProperties().setAckMode(AckMode.MANUAL_IMMEDIATE);

        // 컨슈머 리밸런싱 리스너 설정
        factory.getContainerProperties().setConsumerRebalanceListener(new ConsumerAwareRebalanceListenerImpl());

        return factory;
    }
}