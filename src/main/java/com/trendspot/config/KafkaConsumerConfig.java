package com.trendspot.config;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.TopicPartition;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.SeekToCurrentErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

/**
 * Kafka 消费者配置
 * <p>
 * 配置手动提交模式、重试策略与死信队列
 */
@Configuration
public class KafkaConsumerConfig {

    @Autowired
    private KafkaTemplate<String, Object> kafkaTemplate;

    /**
     * 自定义消费者容器工厂
     * <p>
     * 保留 application.yaml 中的消费者基础配置（地址、序列化器等），
     * 追加手动 ACK + 死信队列 + 重试策略
     */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, Object> kafkaListenerContainerFactory(
            ConsumerFactory<String, Object> consumerFactory) {

        ConcurrentKafkaListenerContainerFactory<String, Object> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);

        // 手动确认模式（与 application.yaml 中的 ack-mode: manual 一致）
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL);

        // 死信队列恢复器：消费失败的消息最终转入 DLT
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
                kafkaTemplate,
                (ConsumerRecord<?, ?> r, Exception e) ->
                        new TopicPartition(KafkaConfig.SECKILL_ORDER_DLT_TOPIC, r.partition())
        );

        // 错误处理器：重试 3 次，间隔 1 秒，均失败后移交死信队列
        SeekToCurrentErrorHandler errorHandler = new SeekToCurrentErrorHandler(
                recoverer, new FixedBackOff(1000L, 3)
        );
        factory.setErrorHandler(errorHandler);

        return factory;
    }
}
