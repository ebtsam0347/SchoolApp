package com.forward.hrms.commons.kafka.producer;

import org.apache.kafka.clients.producer.ProducerConfig;
import org.springframework.boot.kafka.autoconfigure.DefaultKafkaProducerFactoryCustomizer;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;

/**
 * Forces every producer the service creates to be idempotent
 * ({@code enable.idempotence=true}, {@code acks=all}). The outbox poller
 * relies on these guarantees — so does anyone publishing directly.
 *
 * <p>Phase 5 also pins {@code compression.type=zstd} (smaller wire frames
 * for the audit-event traffic) and a 2-minute {@code delivery.timeout.ms}
 * (covers the worst-case retry chain inside the broker without leaving
 * outbox rows hanging in {@code PENDING} forever).
 */
public class KafkaProducerCustomizer implements DefaultKafkaProducerFactoryCustomizer {

    @Override
    public void customize(DefaultKafkaProducerFactory<?, ?> factory) {
        factory.updateConfigs(java.util.Map.of(
                ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true,
                ProducerConfig.ACKS_CONFIG, "all",
                ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, 5,
                ProducerConfig.COMPRESSION_TYPE_CONFIG, "zstd",
                ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, 120_000
        ));
    }
}
