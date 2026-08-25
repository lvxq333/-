package com.trendspot.config;


import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Kafka 配置类
 * <p>
 * 声明秒杀订单消息所需的 Topic
 *
 */
@Configuration
public class KafkaConfig {

    /**
     * 秒杀订单主题名称
     */
    public static final String SECKILL_ORDER_TOPIC = "seckill-order";

    /**
     * 死信队列主题名称（消费失败的消息转入此队列）
     */
    public static final String SECKILL_ORDER_DLT_TOPIC = "seckill-order-dlt";

    /**
     * 创建秒杀订单 Topic
     * <p>
     * 设置 3 个分区，后续 Kafka 消费者可以 3 线程并发消费，
     * 提升订单落库的吞吐量
     */
    @Bean
    public NewTopic seckillOrderTopic() {
        return new NewTopic(SECKILL_ORDER_TOPIC, 3, (short) 1);
    }

    /**
     * 创建死信队列 Topic
     * <p>
     * 单分区即可，死信消息量小且需要保证顺序
     */
    @Bean
    public NewTopic seckillOrderDltTopic() {
        return new NewTopic(SECKILL_ORDER_DLT_TOPIC, 1, (short) 1);
    }
}
