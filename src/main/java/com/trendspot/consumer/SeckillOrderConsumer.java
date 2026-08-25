package com.trendspot.consumer;

import com.trendspot.config.KafkaConfig;
import com.trendspot.dto.SeckillOrderMessage;
import com.trendspot.entity.VoucherOrder;
import com.trendspot.service.IVoucherOrderService;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

/**
 * Kafka 秒杀订单消费者
 * <p>
 * 从 seckill-order 主题消费消息，将秒杀订单异步写入数据库，
 * 实现订单落库与秒杀资格校验的解耦
 */
@Component
@Slf4j
public class SeckillOrderConsumer {

    @Autowired
    private IVoucherOrderService voucherOrderService;
    @Autowired
    private RedissonClient redissonClient;

    /**
     * 消费秒杀订单消息，写库
     * <p>
     * concurrency = 3，与 Topic 分区数一致，3 线程并发消费
     *
     * @param record Kafka 消息记录
     * @param ack    手动确认对象
     */
    @KafkaListener(
            topics = KafkaConfig.SECKILL_ORDER_TOPIC,
            groupId = "seckill-order-group",
            concurrency = "3"
    )
    public void onMessage(org.apache.kafka.clients.consumer.ConsumerRecord<String, SeckillOrderMessage> record,
                          Acknowledgment ack) {
        // 1.解析消息
        SeckillOrderMessage msg = record.value();
        log.debug("收到秒杀订单消息, orderId={}, userId={}, voucherId={}",
                msg.getOrderId(), msg.getUserId(), msg.getVoucherId());

        // 2.构建 VoucherOrder 对象
        VoucherOrder voucherOrder = new VoucherOrder();
        voucherOrder.setId(msg.getOrderId());
        voucherOrder.setUserId(msg.getUserId());
        voucherOrder.setVoucherId(msg.getVoucherId());

        // 3.获取分布式锁，锁粒度：用户+优惠券，防止同一用户对同一券并发写 DB
        String lockKey = "lock:order:" + msg.getUserId() + ":" + msg.getVoucherId();
        RLock lock = redissonClient.getLock(lockKey);
        boolean isLock = lock.tryLock();
        if (!isLock) {
            // 获取锁失败，说明同一用户+券的订单正在处理中，幂等保护会兜底
            log.warn("获取锁失败，跳过本消息, userId={}, voucherId={}", msg.getUserId(), msg.getVoucherId());
            ack.acknowledge();
            return;
        }

        try {
            // 4.调用 @Transactional 方法落库（此处是外部调用，Spring 注入的 proxy 会使事务生效）
            voucherOrderService.creatVoucherOrder(voucherOrder);
        } catch (Exception e) {
            // 异常抛出后不会 ACK，Kafka 会重试（3次/间隔1秒），均失败后进入死信队列
            log.error("订单落库失败，将重试, orderId={}", msg.getOrderId(), e);
            throw e;
        } finally {
            // 5.释放锁
            lock.unlock();
        }

        // 6.手动 ACK，确认消息消费成功
        ack.acknowledge();
    }
}
