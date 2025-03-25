package com.tenacy.pixiescale.mediaingestion.config;

import com.tenacy.pixiescale.mediaingestion.domain.MediaMetadata;
import com.tenacy.pixiescale.mediaingestion.service.EventPublisher;
import com.tenacy.pixiescale.mediaingestion.service.MetadataExtractor;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.kafka.test.context.EmbeddedKafka;
import reactor.core.publisher.Mono;

@TestConfiguration
@EmbeddedKafka(partitions = 1, topics = {"media-uploaded-test"})
public class TestConfig {
    @Bean
    @Primary
    public EventPublisher mockEventPublisher() {
        return event -> Mono.empty();
    }

    @Bean
    @Primary
    public MetadataExtractor mockMetadataExtractor() {
        return path -> Mono.just(
                MediaMetadata.builder()
                        .format("mp4")
                        .width(1920)
                        .height(1080)
                        .codec("h264")
                        .duration(120.0)
                        .bitrate(5000)
                        .frameRate(30.0)
                        .build()
        );
    }
}