package com.hmdp.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Kafka 秒杀订单消息体
 * <p>
 * 从生产者（秒杀服务）传递到消费者（订单落库服务）
 *
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class SeckillOrderMessage {

    /**
     * 用户 ID
     */
    private Long userId;

    /**
     * 优惠券 ID
     */
    private Long voucherId;

    /**
     * 全局唯一订单 ID（由 RedisIdWork 生成）
     */
    private Long orderId;

    /**
     * 消息生成时间戳（毫秒）
     */
    private Long timestamp;
}
