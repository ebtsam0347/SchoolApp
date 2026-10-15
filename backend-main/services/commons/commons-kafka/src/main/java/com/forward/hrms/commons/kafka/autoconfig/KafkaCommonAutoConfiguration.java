package com.forward.hrms.commons.kafka.autoconfig;

import com.forward.hrms.commons.kafka.consumer.RetryAndDlqErrorHandler;
import com.forward.hrms.commons.kafka.producer.KafkaProducerCustomizer;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DefaultErrorHandler;

@AutoConfiguration
@ConditionalOnClass(KafkaTemplate.class)
public class KafkaCommonAutoConfiguration {

    @Bean
    public KafkaProducerCustomizer kafkaProducerCustomizer() {
        return new KafkaProducerCustomizer();
    }

    @Bean
    @ConditionalOnMissingBean
    public DefaultErrorHandler kafkaErrorHandler(KafkaOperations<String, String> producer) {
        return RetryAndDlqErrorHandler.create(producer);
    }
}
