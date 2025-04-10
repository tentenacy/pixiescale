package com.tenacy.pixiescale.transcodingworker.config;

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.common.TopicPartition;
import org.springframework.kafka.listener.ConsumerAwareRebalanceListener;

import java.util.Collection;

@Slf4j
public class ConsumerAwareRebalanceListenerImpl implements ConsumerAwareRebalanceListener {

    @Override
    public void onPartitionsRevokedBeforeCommit(Consumer<?, ?> consumer, Collection<TopicPartition> partitions) {
        if (!partitions.isEmpty()) {
            log.info("Partitions revoked: {}", partitions);
        }
    }

    @Override
    public void onPartitionsAssigned(Consumer<?, ?> consumer, Collection<TopicPartition> partitions) {
        if (!partitions.isEmpty()) {
            log.info("Partitions assigned: {}", partitions);
        }
    }
}