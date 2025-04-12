package com.tenacy.pixiescale.transcodingworker.config;

import com.tenacy.pixiescale.common.event.TranscodingTaskEvent;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.kafka.listener.ContainerProperties.AckMode;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Configuration
public class KafkaConfig {

    @Value("${app.transcoding.max-concurrent-tasks:2}")
    private int maxConcurrentTasks;

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Value("${app.kafka.topics.media-uploaded}")
    private String mediaUploadedTopic;

    @Value("${app.kafka.topics.job-created}")
    private String jobCreatedTopic;

    @Value("${app.kafka.topics.job-updated}")
    private String jobUpdatedTopic;

    @Value("${app.kafka.topics.transcoding-task}")
    private String transcodingTaskTopic;

    @Value("${app.kafka.topics.task-result}")
    private String taskResultTopic;

    @Value("${app.kafka.topics.storage-result}")
    private String storageResultTopic;

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

    @Bean
    public KafkaAdmin kafkaAdmin() {
        Map<String, Object> configs = new HashMap<>();
        configs.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        return new KafkaAdmin(configs);
    }

    @Bean
    public List<NewTopic> kafkaTopics() {
        return Arrays.asList(
                new NewTopic(mediaUploadedTopic, 1, (short) 1),
                new NewTopic(jobCreatedTopic, 1, (short) 1),
                new NewTopic(jobUpdatedTopic, 1, (short) 1),
                new NewTopic(transcodingTaskTopic, 10, (short) 1), // 파티션 10개
                new NewTopic(taskResultTopic, 1, (short) 1),
                new NewTopic(storageResultTopic, 1, (short) 1)
        );
    }

    @Bean
    public NewTopic mediaUploadedTopic() {
        return new NewTopic(mediaUploadedTopic, 1, (short) 1);
    }

    @Bean
    public NewTopic jobCreatedTopic() {
        return new NewTopic(jobCreatedTopic, 1, (short) 1);
    }

    @Bean
    public NewTopic jobUpdatedTopic() {
        return new NewTopic(jobUpdatedTopic, 1, (short) 1);
    }

    @Bean
    public NewTopic transcodingTaskTopic() {
        return new NewTopic(transcodingTaskTopic, 10, (short) 1);
    }

    @Bean
    public NewTopic taskResultTopic() {
        return new NewTopic(taskResultTopic, 1, (short) 1);
    }

    @Bean
    public NewTopic storageResultTopic() {
        return new NewTopic(storageResultTopic, 1, (short) 1);
    }
}