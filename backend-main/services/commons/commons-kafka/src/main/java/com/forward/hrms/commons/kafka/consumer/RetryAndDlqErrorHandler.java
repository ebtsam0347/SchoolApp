package com.forward.hrms.commons.kafka.consumer;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.ExponentialBackOff;

/**
 * Default consumer error handler — exponential backoff (1m start, ×4),
 * cap of 1 hour. After exhaustion, the failed record is published to
 * {@code <source-topic>.dlq} with the same partition.
 */
public final class RetryAndDlqErrorHandler {

    private RetryAndDlqErrorHandler() {}

    public static DefaultErrorHandler create(KafkaOperations<?, ?> producer) {
        DeadLetterPublishingRecoverer dlq = new DeadLetterPublishingRecoverer(
                producer,
                (record, ex) -> new org.apache.kafka.common.TopicPartition(record.topic() + ".dlq", record.partition()));

        ExponentialBackOff backOff = new ExponentialBackOff(60_000L, 4.0);
        backOff.setMaxInterval(3_600_000L);
        backOff.setMaxElapsedTime(6 * 3_600_000L);

        return new DefaultErrorHandler(dlq, backOff);
    }
}
