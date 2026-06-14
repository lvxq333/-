package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.CacheClient;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RedisData;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

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
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private CacheClient cacheClient;

    /**
     * 根据id查询商铺信息
     *
     * @param id
     * @return
     */
    @Override
    public Result queryById(Long id) {
        String keyPrefix = RedisConstants.CACHE_SHOP_KEY;
        String lockKeyPrefix = RedisConstants.LOCK_SHOP_KEY;
        // 缓存穿透
//        Shop shop = cacheClient.queryWithPassThrough(
//                key, id, Shop.class, this::getById, RedisConstants.CACHE_SHOP_TTL,
//                TimeUnit.MINUTES);

        // 互斥锁解决缓存击穿
//        Shop shop = queryWithMutex(id);
        

        // 逻辑过期解决缓存击穿
//        Shop shop = queryWithLogicalExpire(id);
        Shop shop = cacheClient.queryWithLogicalExpire(
                keyPrefix, lockKeyPrefix, id, Shop.class, this::getById,
                RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);
        if (shop == null) {
            return Result.fail("店铺不存在");
        }
        return Result.ok(shop);
    }

    /**
     * 缓存穿透
     *
     * @param id
     * @return
     */
//    public Shop queryWithPassThroug(Long id) {
//        // 1.根据id查询redis中商铺信息
//        String key = RedisConstants.CACHE_SHOP_KEY + id;
//        String cacheShop = stringRedisTemplate.opsForValue().get(key);
//        if (StrUtil.isNotBlank(cacheShop)) {
//            // 存在，返回商铺信息
//            Shop shop = JSONUtil.toBean(cacheShop, Shop.class);
//            return shop;
//        }
//        // 如果店铺信息为空值，直接返回——对应缓存穿透
//        if (cacheShop != null) {
//            return null;
//        }
//        // 缓存重建
//        // 获取互斥锁
//        String lockKey = RedisConstants.LOCK_SHOP_KEY + id;
//        // 设定一个shop对象，用于接收数据库中的数据
//        Shop shop = null;
//        try {
//            if (!trylock(lockKey)) {
//                // 获取锁失败，休眠一段时间再次重试
//                Thread.sleep(50);
//                return queryWithMutex(id);
//            }
//            // 2.redis中不存在，根据id查询数据库
//            shop = getById(id);
//            // 模拟数据库查找数据的长耗时
//            Thread.sleep(200);
//            // 3.数据库中不存在，写入空值——缓存穿透解决方案：设置空值并设置过期时间
//            if (shop == null) {
//                // 将空值写入Redis
//                stringRedisTemplate.opsForValue()
//                        .set(key, "",
//                                RedisConstants.CACHE_NULL_TTL, TimeUnit.MINUTES);
//                return null;
//            }
//            // 4.存在，将商铺信息写入Redis
//            stringRedisTemplate.opsForValue()
//                    .set(key, JSONUtil.toJsonStr(shop),
//                            RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);
//        } catch (InterruptedException e) {
//            throw new RuntimeException(e);
//        } finally {
//            unLock(lockKey);
//        }
//        // 5.返回商铺信息
//        return shop;
//    }

    /**
     * 缓存击穿——互斥锁解决缓存穿透
     *
     * @param id
     * @return
     */
//    public Shop queryWithMutex(Long id) {
//        // 1.根据id查询redis中商铺信息
//        String key = RedisConstants.CACHE_SHOP_KEY + id;
//        String cacheShop = stringRedisTemplate.opsForValue().get(key);
//        if (StrUtil.isNotBlank(cacheShop)) {
//            // 存在，返回商铺信息
//            Shop shop = JSONUtil.toBean(cacheShop, Shop.class);
//            return shop;
//        }
//        // 如果店铺信息为空值，直接返回
//        if (cacheShop != null) {
//            return null;
//        }
//        // 2.redis中不存在，根据id查询数据库
//        Shop shop = getById(id);
//        // 3.数据库中不存在，返回404
//        if (shop == null) {
//            // 将空值写入Redis
//            stringRedisTemplate.opsForValue()
//                    .set(key, "",
//                            RedisConstants.CACHE_NULL_TTL, TimeUnit.MINUTES);
//            return null;
//        }
//        // 4.存在，将商铺信息写入Redis
//        stringRedisTemplate.opsForValue()
//                .set(key, JSONUtil.toJsonStr(shop),
//                        RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);
//        // 5.返回商铺信息
//        return shop;
//    }

    // 线程池
    public static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    /**
     * 缓存击穿——逻辑过期解决缓存击穿
     *
     * @param id
     * @return
     */
//    public Shop queryWithLogicalExpire(Long id) {
//        // 1.根据id查询redis中商铺信息
//        String key = RedisConstants.CACHE_SHOP_KEY + id;
//        String shopJson = stringRedisTemplate.opsForValue().get(key);
//        if (StrUtil.isBlank(shopJson)) {
//            log.info("店铺不存在");
//            // 不存在，返回空值
//            return null;
//        }
//        // 2.命中，需要先把json数据转为对象
//        RedisData redisData = JSONUtil.toBean(shopJson, RedisData.class);
//        // 获取店铺信息
//        Shop shop = JSONUtil.toBean((JSONObject) redisData.getData(), Shop.class);
//        LocalDateTime expireTime = redisData.getExpireTime();
//        // 判断是否过期
//        if (expireTime.isAfter(LocalDateTime.now())) {
//            // 未过期，直接返回
//            return shop;
//        }
//        // 过期，需要缓存重建
//        // 1.获取互斥锁
//        String lockKey = RedisConstants.LOCK_SHOP_KEY + id;
//        if (!trylock(lockKey)) {
//            // 获取锁失败，返回旧的商铺信息
//            return shop;
//        }
//        // 获取锁成功，再次查询redis缓存，查看是否过期——目的：
//        // 存在可能，当前线程判断过期，此时其他线程已经重建完并释放锁，当前线程获取到锁，
//        // 并不知道已经重建完，会再次重建，因此需要再次判断
//        log.info("获取锁成功，准备二次检查，id={}", id);
//        shopJson = stringRedisTemplate.opsForValue().get(key);
//        // 过期，则需要先把json数据转为对象
//        redisData = JSONUtil.toBean(shopJson, RedisData.class);
//        // 获取店铺信息
//        shop = JSONUtil.toBean((JSONObject) redisData.getData(), Shop.class);
//        expireTime = redisData.getExpireTime();
//        if (expireTime.isAfter(LocalDateTime.now())) {
//            log.info("二次检查发现缓存已被重建，释放锁，id={}", id);
//            // 未过期，直接返回
//            unLock(lockKey);
//            return shop;
//        }
//        // 过期，开启独立线程，实现缓存重建
//        // 2.redis中不存在，根据id查询数据库
//        CACHE_REBUILD_EXECUTOR.submit(() -> {
//            try {
//                log.info("开始异步缓存重建，id={}", id);
//                // 缓存重建
//                this.saveShop2Redis(id, 20L);
//                log.info("异步缓存重建完成，id={}", id);
//            } catch (Exception e) {
//                log.error("异步缓存重建失败，id={}", id, e);
//                throw new RuntimeException(e);
//            } finally {
//                log.info("释放锁，id={}", id);
//                // 释放锁
//                unLock(lockKey);
//            }
//        });
//        // 5.返回商铺信息
//        return shop;
//    }

    /**
     * 更新商铺信息
     *
     * @param shop
     */
    @Override
    @Transactional
    public Result update(Shop shop) {
        Long id = shop.getId();
        if (id == null) {
            return Result.fail("店铺id不能为空");
        }
        // 1.更新商铺信息
        updateById(shop);
        // 2.删除Redis中的商铺信息
        stringRedisTemplate.delete(RedisConstants.CACHE_SHOP_KEY + id);
        return Result.ok();
    }

    /**
     * 尝试获取锁
     *
     * @param key
     * @return
     */
//    private boolean trylock(String key) {
//        Boolean flag = stringRedisTemplate.opsForValue()
//                .setIfAbsent(key, "1", RedisConstants.CACHE_NULL_TTL, TimeUnit.MINUTES);
//        return BooleanUtil.isTrue(flag);
//    }

    /**
     * 释放锁
     *
     * @param key
     */
//    private void unLock(String key) {
//        stringRedisTemplate.delete(key);
//    }

    /**
     * redis缓存重建
     *
     * @param id
     * @param expireSeconds
     */
    public void saveShop2Redis(Long id, Long expireSeconds) throws InterruptedException {
        // 1.查询店铺数据
        Shop shop = getById(id);
        Thread.sleep(200);
        // 2.封装逻辑过期时间
        RedisData redisData = new RedisData();
        redisData.setData(shop);
        // 设置逻辑过期时间,其实是设置在创建时间的基础上经过多少秒后失效，但失效后数据不会消失，
        // 也就是这个redisData是永久的，如果不修改或者删除的话
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(expireSeconds));
        // 3.写入Redis
        stringRedisTemplate.opsForValue()
                .set(RedisConstants.CACHE_SHOP_KEY + id, JSONUtil.toJsonStr(redisData));
    }
}
