package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisIdWork;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.apache.tomcat.jni.Time;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.AopContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
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
    @Resource
    private RedissonClient redissonClient;

    // 创建redis秒杀脚本
    private static final DefaultRedisScript<Long> SECKILL_SCRIPT;

    static {
        // 初始化
        SECKILL_SCRIPT = new DefaultRedisScript<>();
        // 设置脚本路径
        SECKILL_SCRIPT.setLocation(new ClassPathResource("seckill.lua"));
        // 设置返回类型
        SECKILL_SCRIPT.setResultType(Long.class);
    }

    // 创建线程池
    private static final ExecutorService SECKILL_ORDER_EXECUTOR = Executors.newSingleThreadExecutor();

    /**
     * 创建线程, 处理优惠券订单信息
     */
    @PostConstruct
    private void init() {
        SECKILL_ORDER_EXECUTOR.submit(new VoucherOrderHandler());
    }

    /**
     * 优惠券订单处理，获取消息队列中的订单信息，进行下单处理——线程任务
     */
    private class VoucherOrderHandler implements Runnable{

        String queueName = "stream.orders";
        @Override
        public void run() {
            while (true) {
                try {
                    // 1.获取消息队列中的订单信息—— >:最近一条未消费的消息
                    // xreadgroup group g1 c1 count 1 block 2000 stream stream.orders >
                    List<MapRecord<String, Object, Object>> list = stringRedisTemplate.opsForStream().read(
                            Consumer.from("g1", "c1"),
                            StreamReadOptions.empty().count(1).block(Duration.ofSeconds(2L)),
                            StreamOffset.create(queueName, ReadOffset.lastConsumed())
                    );
                    // 判断消息获取是否成功
                    if (list == null || list.isEmpty()){
                        // 获取失败，返回或者重试
                        continue;
                    }
                    // 获取成功，下单
                    // 获取订单信息
                    MapRecord<String, Object, Object> record = list.get(0);
                    Map<Object, Object> values = record.getValue();
                    VoucherOrder voucherOrder = BeanUtil.fillBeanWithMap(values, new VoucherOrder(), true);
                    // 将订单信息写入数据库
                    handleVoucherOrder(voucherOrder);
                    // ACK确认
                    stringRedisTemplate.opsForStream().acknowledge(queueName, "g1",record.getId());


                } catch (Exception e) {
                    log.error("处理订单异常", e);
                    handlePendingList();
                }
            }
        }

        /**
         * 处理pending-list中的消息
         */
        private void handlePendingList() {
            while (true) {
                try {
                    // 1.获取pending-list中的订单信息—— 0代表读取penging-list消息
                    // xreadgroup group g1 c1 count 1 block 2000 stream stream.orders 0
                    List<MapRecord<String, Object, Object>> list = stringRedisTemplate.opsForStream().read(
                            Consumer.from("g1", "c1"),
                            StreamReadOptions.empty().count(1).block(Duration.ofSeconds(2L)),
                            StreamOffset.create(queueName, ReadOffset.from("0"))
                    );
                    // 判断消息获取是否成功
                    if (list == null || list.isEmpty()){
                        // pending-list中没有消息，结束循环
                        break;
                    }
                    // 获取成功，下单
                    // 获取订单信息
                    MapRecord<String, Object, Object> record = list.get(0);
                    Map<Object, Object> values = record.getValue();
                    VoucherOrder voucherOrder = BeanUtil.fillBeanWithMap(values, new VoucherOrder(), true);
                    // 将订单信息写入数据库
                    handleVoucherOrder(voucherOrder);
                    // ACK确认
                    stringRedisTemplate.opsForStream().acknowledge(queueName, "g1",record.getId());
                } catch (Exception e) {
                    log.error("处理订单异常", e);
                    try {
                        Thread.sleep(20);
                    } catch (InterruptedException ex) {
                        throw new RuntimeException(ex);
                    }
                }
            }
        }
    }



//    // 创建阻塞队列
//    private BlockingQueue<VoucherOrder> orderTasks = new ArrayBlockingQueue<>(1024*1024);
//    /**
//     * 优惠券订单处理，获取阻塞队列中的订单信息，进行下单处理——线程任务
//     */
//    private class VoucherOrderHandler implements Runnable{
//        @Override
//        public void run() {
//            while (true) {
//                try {
//                    // 1.获取队列中的订单信息
//                    VoucherOrder voucherOrder = orderTasks.take();
//                    // 2.创建订单
//                    handleVoucherOrder(voucherOrder);
//                } catch (Exception e) {
//                    log.error("处理订单异常", e);
//                }
//            }
//        }
//    }

    private IVoucherOrderService proxy;

    /**
     * 异步处理优惠券订单
     * @param voucherOrder
     */
    private void handleVoucherOrder(VoucherOrder voucherOrder){
        // 1.获取用户
        Long userId = voucherOrder.getUserId();
        // 2.创建锁对象
        RLock redisLock = redissonClient.getLock("lock:order:" + userId);
        // 3.获取锁
        boolean isLock = redisLock.tryLock();
        // 4.判断获取锁成功与否
        if (!isLock) {
            // 获取锁失败，返回错误或者重试
            log.info("不允许重复下单！");
            return;
        }
        try {
            proxy.creatVoucherOrder(voucherOrder);
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            redisLock.unlock();
        }
    }

    /**
     * 秒杀优惠券
     *
     * @param voucherId 优惠券id
     * @return 订单id
     */
    @Override
    public Result seckillVoucher(Long voucherId) {
        // 获取用户id
        Long userId = UserHolder.getUser().getId();
        // 获取订单id
        long orderId = redisIdWork.nextId("order");
        // 1.执行lua脚本
        Long result = stringRedisTemplate.execute(
                SECKILL_SCRIPT,      // Lua脚本
                Collections.emptyList(),
                voucherId.toString(),
                userId.toString(),
                String.valueOf(orderId)
        );
        // 2.判断结果是否为0     0 为成功
        if (result.intValue() != 0) {
            // 2.1不为0，代表没有秒杀资格
            return Result.fail(result.intValue() == 1 ? "库存不足" : "不能重复下单");
        }
        // 3. 获取代理对象
        proxy = (IVoucherOrderService) AopContext.currentProxy();
        // 4.返回订单id
        return Result.ok(orderId);
    }

//    public Result seckillVoucher(Long voucherId) {
//        Long userId = UserHolder.getUser().getId();
//        long orderId = redisIdWork.nextId("order");
//        // 1.执行lua脚本
//        Long result = stringRedisTemplate.execute(
//                SECKILL_SCRIPT,      // Lua脚本
//                Collections.emptyList(),
//                voucherId.toString(),
//                userId.toString(),
//                String.valueOf(orderId)
//        );
//        // 2.判断结果是否为0     0 为成功
//        if (result.intValue() != 0) {
//            // 2.1不为0，代表没有秒杀资格
//            return Result.fail(result.intValue() == 1 ? "库存不足" : "不能重复下单");
//        }
//        // 2.2为0，代表有秒杀资格，把下单信息添加到阻塞队列
//        VoucherOrder voucherOrder = new VoucherOrder();
//        voucherOrder.setId(orderId);
//        voucherOrder.setUserId(userId);
//        voucherOrder.setVoucherId(voucherId);
//        // 添加到阻塞队列
//        orderTasks.add(voucherOrder);
//        proxy = (IVoucherOrderService) AopContext.currentProxy();
//        // 3.返回订单id
//        return Result.ok(orderId);
//    }

//    public Result seckillVoucher(Long voucherId) {
//        // 1.查询优惠券信息
//        SeckillVoucher voucher = seckillVoucherService.getById(voucherId);
//        // 2.判断秒杀是否开始
//        if (voucher.getBeginTime().isAfter(LocalDateTime.now())) {
//            // 未开始，返回异常结果
//            return Result.fail("秒杀尚未开始");
//        }
//        // 3.开始且还未结束，判断库存是否充足
//        if (voucher.getEndTime().isBefore(LocalDateTime.now())) {
//            // 4.1 秒杀已结束
//            return Result.fail("秒杀已结束");
//        }
//        // 4 库存不足，返回异常结果
//        if (voucher.getStock() < 1) {
//            return Result.fail("库存不足");
//        }
//        Long userId = UserHolder.getUser().getId();
//        // 5.一人一单，判断用户是否重复抢购
//        // 5.1 获取锁
//        SimpleRedisLock lock = new SimpleRedisLock("order:" + userId, stringRedisTemplate);
//        boolean isLock = lock.tryLock(1200);
//        if (!isLock) {
//            // 获取锁失败，返回异常结果
//            return Result.fail("不允许重复下单");
//        }
//        try {
//            IVoucherOrderService proxy = (IVoucherOrderService)AopContext.currentProxy();
//            return proxy.creatVoucherOrder(voucherId);
//        } catch (IllegalStateException e) {
//            throw new RuntimeException(e);
//        } finally {
//            lock.unLock();
//        }
//
////        synchronized (userId.toString().intern()){
////            IVoucherOrderService proxy = (IVoucherOrderService)AopContext.currentProxy();
////            return proxy.creatVoucherOrder(voucherId);      // TODO 解释这里为什么不用this.creatVoucherOrder()
////        }
//    }

    /**
     * 创建优惠券订单
     *
     * @param voucherOrder 优惠券订单
     * @return 订单id
     */
    @Transactional
    public void creatVoucherOrder(VoucherOrder voucherOrder) {
        // -----------一人一单-----------------
        // 5 用户id
        Long userId = voucherOrder.getUserId();
        Long voucherId = voucherOrder.getVoucherId();
        Integer count = query()
                .eq("user_id", userId)
                .eq("voucher_id", voucherId)
                .count();
        if (count > 0){
            log.info("用户已抢购");
            return;
        }
        // 6.库存充足，扣减库存，生成订单
        boolean success = seckillVoucherService.update()
                .setSql("stock = stock - 1")        // set stock = stock - 1
                .eq("voucher_id", voucherId)         // where id = ?
                .gt("stock", 0)     // where stock > 0    gt >, lt <
                .update();
        if (!success) {
            // 扣减库存失败，返回异常结果
            log.info("库存不足");
            return;
        }
        save(voucherOrder);         // 6.4 保存订单到数据库
    }
}
