package com.hmdp.service.impl;

import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisIdWork;
import com.hmdp.utils.SimpleRedisLock;
import com.hmdp.utils.UserHolder;
import org.springframework.aop.framework.AopContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private ISeckillVoucherService seckillVoucherService;
    @Autowired
    private RedisIdWork redisIdWork;

    /**
     * 秒杀优惠券
     *
     * @param voucherId 优惠券id
     * @return 订单id
     */
    @Override
    public Result seckillVoucher(Long voucherId) {
        // 1.查询优惠券信息
        SeckillVoucher voucher = seckillVoucherService.getById(voucherId);
        // 2.判断秒杀是否开始
        if (voucher.getBeginTime().isAfter(LocalDateTime.now())) {
            // 未开始，返回异常结果
            return Result.fail("秒杀尚未开始");
        }
        // 3.开始且还未结束，判断库存是否充足
        if (voucher.getEndTime().isBefore(LocalDateTime.now())) {
            // 4.1 秒杀已结束
            return Result.fail("秒杀已结束");
        }
        // 4 库存不足，返回异常结果
        if (voucher.getStock() < 1) {
            return Result.fail("库存不足");
        }
        Long userId = UserHolder.getUser().getId();
        // 5.一人一单，判断用户是否重复抢购
        // 5.1 获取锁
        SimpleRedisLock lock = new SimpleRedisLock("order:" + userId, stringRedisTemplate);
        boolean isLock = lock.tryLock(1200);
        if (!isLock) {
            // 获取锁失败，返回异常结果
            return Result.fail("不允许重复下单");
        }
        try {
            IVoucherOrderService proxy = (IVoucherOrderService)AopContext.currentProxy();
            return proxy.creatVoucherOrder(voucherId);
        } catch (IllegalStateException e) {
            throw new RuntimeException(e);
        } finally {
            lock.unLock();
        }

//        synchronized (userId.toString().intern()){
//            IVoucherOrderService proxy = (IVoucherOrderService)AopContext.currentProxy();
//            return proxy.creatVoucherOrder(voucherId);      // TODO 解释这里为什么不用this.creatVoucherOrder()
//        }
    }

    /**
     * 创建优惠券订单
     *
     * @param voucherId 优惠券id
     * @return 订单id
     */
    @Transactional
    public Result creatVoucherOrder(Long voucherId) {
        // -----------一人一单-----------------
        // 5 用户id
        Long userId = UserHolder.getUser().getId();
        Integer count = query()
                .eq("user_id", userId)
                .eq("voucher_id", voucherId)
                .count();
        if (count > 0){
            return Result.fail("用户已抢购");
        }
        // 6.库存充足，扣减库存，生成订单
        boolean success = seckillVoucherService.update()
                .setSql("stock = stock - 1")        // set stock = stock - 1
                .eq("voucher_id", voucherId)         // where id = ?
                .gt("stock", 0)     // where stock > 0    gt >, lt <
                .update();
        if (!success) {
            // 扣减库存失败，返回异常结果
            return Result.fail("库存不足");
        }
        // 7.创建订单
        VoucherOrder voucherOrder = new VoucherOrder();
        // 7.1 订单id
        long orderId = redisIdWork.nextId("order");
        voucherOrder.setId(orderId);
        voucherOrder.setUserId(userId);
        // 7.3 优惠券id
        voucherOrder.setVoucherId(voucherId);
        save(voucherOrder);         // 6.4 保存订单到数据库
        // 8.返回订单id
        return Result.ok(orderId);
    }
}
