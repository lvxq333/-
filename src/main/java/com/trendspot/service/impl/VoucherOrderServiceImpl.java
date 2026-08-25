package com.trendspot.service.impl;

import com.trendspot.config.KafkaConfig;
import com.trendspot.dto.Result;
import com.trendspot.dto.SeckillOrderMessage;
import com.trendspot.entity.VoucherOrder;
import com.trendspot.mapper.VoucherOrderMapper;
import com.trendspot.service.ISeckillVoucherService;
import com.trendspot.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.trendspot.utils.RedisIdWork;
import com.trendspot.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.Collections;

/**
 * <p>
 * 服务实现类
 * </p>
 */
@Service
@Slf4j
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private ISeckillVoucherService seckillVoucherService;
    @Autowired
    private RedisIdWork redisIdWork;
    @Autowired
    private KafkaTemplate<String, Object> kafkaTemplate;

    // 创建redis秒杀脚本
    private static final DefaultRedisScript<Long> SECKILL_SCRIPT;
    // 创建redis库存回滚脚本
    private static final DefaultRedisScript<Long> ROLLBACK_SCRIPT;

    static {
        // 初始化秒杀脚本
        SECKILL_SCRIPT = new DefaultRedisScript<>();
        SECKILL_SCRIPT.setLocation(new ClassPathResource("seckill.lua"));
        SECKILL_SCRIPT.setResultType(Long.class);
        // 初始化库存回滚脚本
        ROLLBACK_SCRIPT = new DefaultRedisScript<>();
        ROLLBACK_SCRIPT.setLocation(new ClassPathResource("rollback.lua"));
        ROLLBACK_SCRIPT.setResultType(Long.class);
    }

    /**
     * 秒杀优惠券
     *
     * @param voucherId 优惠券id
     * @return 订单id
     */
    @Override
    public Result seckillVoucher(Long voucherId) {
        // 1.获取用户id
        Long userId = UserHolder.getUser().getId();
        // 2.生成全局唯一订单id
        long orderId = redisIdWork.nextId("order");
        // 3.执行lua脚本，完成资格校验与库存扣减（不写入消息队列，已解耦至 Kafka）
        Long result = stringRedisTemplate.execute(
                SECKILL_SCRIPT,
                Collections.emptyList(),
                voucherId.toString(),
                userId.toString()
        );
        // 4.判断结果是否为0（0 为成功）
        if (result.intValue() != 0) {
            // 不为0，代表没有秒杀资格，区分具体失败原因
            if (result.intValue() == 1) {
                // 库存不足
                return Result.fail("库存不足");
            }
            if (result.intValue() == 2) {
                // 不能重复下单
                return Result.fail("不能重复下单");
            }
        }
        // 5.构建 Kafka 消息并发送
        SeckillOrderMessage message = new SeckillOrderMessage(
                userId,
                voucherId,
                orderId,
                System.currentTimeMillis()
        );
        try {
            // 以 voucherId 作为 key，保证同一券的消息进入同一分区，消费时有序
            kafkaTemplate.send(
                    KafkaConfig.SECKILL_ORDER_TOPIC,
                    String.valueOf(voucherId),
                    message
            );
        } catch (Exception e) {
            // Kafka 发送失败，执行 Redis 回滚（Lua 脚本保证原子性）
            log.error("Kafka 发送失败，回滚 Redis 库存, voucherId={}, userId={}", voucherId, userId, e);
            stringRedisTemplate.execute(
                    ROLLBACK_SCRIPT,
                    Collections.emptyList(),
                    voucherId.toString(),
                    userId.toString()
            );
            return Result.fail("加载失败，请重新尝试");
        }
        // 6.返回订单id（订单落库由 Kafka 消费者异步处理）
        return Result.ok(orderId);
    }

    /**
     * 创建优惠券订单
     *
     * @param voucherOrder 优惠券订单
     */
    @Transactional
    public void creatVoucherOrder(VoucherOrder voucherOrder) {
        // -----------一人一单-----------------
        // 1.获取用户id和优惠券id
        Long userId = voucherOrder.getUserId();
        Long voucherId = voucherOrder.getVoucherId();
        // 2.检查用户是否已经下过单（DB 层面的最终幂等兜底）
        Integer count = query()
                .eq("user_id", userId)
                .eq("voucher_id", voucherId)
                .count();
        if (count > 0) {
            // 用户已抢购过该优惠券，直接返回
            log.info("用户已抢购 userId={} voucherId={}", userId, voucherId);
            return;
        }
        // 3.扣减库存（乐观锁，防止超卖）
        boolean success = seckillVoucherService.update()
                .setSql("stock = stock - 1")        // set stock = stock - 1
                .eq("voucher_id", voucherId)         // where voucher_id = ?
                .gt("stock", 0)     // where stock > 0    gt >, lt <
                .update();
        if (!success) {
            // 扣减库存失败，库存不足
            log.info("库存不足 voucherId={}", voucherId);
            return;
        }
        // 4.保存订单到数据库
        save(voucherOrder);
    }
}
