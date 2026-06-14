package com.hmdp.utils;


import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

@Slf4j
@Component
@AllArgsConstructor
public class CacheClient {

    private final StringRedisTemplate stringRedisTemplate;

    /**
     * 缓存数据
     * @param key 缓存的key
     * @param value 缓存的数据
     * @param time 缓存的时间
     * @param unit 时间单位
     */
    public void set(String key, Object value, Long time, TimeUnit unit) {
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value), time, unit);
    }

    /**
     * 缓存逻辑过期数据
     * @param key 缓存的key
     * @param value 缓存的数据
     * @param time 缓存的时间
     * @param unit 时间单位
     */
    public void setWithLogicExpire(String key, Object value, Long time, TimeUnit unit) {
        RedisData redisData = new RedisData();
        redisData.setData(value);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(unit.toSeconds(time)));
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redisData));
    }

    /**
     * 解决redis缓存穿透
     * @param keyPrefix 前缀
     * @param id id
     * @param type 类型
     * @param dbFallback 数据库查询方法
     * @param time 过期时间
     * @param unit 时间单位
     * @return
     */
    public <T, ID> T queryWithPassThrough(
            String keyPrefix, ID id , Class<T> type, Function<ID, T> dbFallback,Long time, TimeUnit unit
    ) {
        // 1.根据id查询redis中商铺信息
        String key = keyPrefix + id;
        String json = stringRedisTemplate.opsForValue().get(key);
        if (StrUtil.isNotBlank(json)) {
            // 存在，返回查询到的信息
            return JSONUtil.toBean(json, type);
        }
        // 如果信息为空值，直接返回
        if (json != null) {
            return null;
        }
        // 2.redis中不存在，根据id查询数据库
        T t = dbFallback.apply(id);
        // 3.数据库中不存在，返回404
        if (t == null) {
            // 将空值写入Redis
            stringRedisTemplate.opsForValue()
                    .set(key, "",
                            RedisConstants.CACHE_NULL_TTL, TimeUnit.MINUTES);
            return null;
        }
        // 4.存在，将商铺信息写入Redis
        this.set(key, t, time, unit);
        // 5.返回商铺信息
        return t;
    }

    //线程池
    public static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    /**
     * 缓存击穿——逻辑过期解决缓存击穿
     *
     * @param id
     * @return
     */
    public <ID,T> T queryWithLogicalExpire(
            String keyPrefix,String lockKeyPrefix, ID id , Class<T> type,
            Function<ID, T> dbFallback,Long time, TimeUnit unit
    ) {
        // 1.根据id查询redis中商铺信息
        String key = keyPrefix + id;
        String json = stringRedisTemplate.opsForValue().get(key);
        if (StrUtil.isBlank(json)) {
            // 不存在，返回空值
            return null;
        }
        // 2.命中，需要先把json数据转为对象
        RedisData redisData = JSONUtil.toBean(json, RedisData.class);
        // 获取店铺信息
        T data = JSONUtil.toBean((JSONObject) redisData.getData(), type);
        LocalDateTime expireTime = redisData.getExpireTime();
        // 判断是否过期
        if (expireTime.isAfter(LocalDateTime.now())) {
            // 未过期，直接返回
            return data;
        }
        // 过期，需要缓存重建
        // 1.获取互斥锁
        String lockKey = lockKeyPrefix + id;
        if (!trylock(lockKey)) {
            // 获取锁失败，返回旧的商铺信息
            return data;
        }
        // 获取锁成功，再次查询redis缓存，查看是否过期——目的：
        // 存在可能，当前线程判断过期，此时其他线程已经重建完并释放锁，当前线程获取到锁，
        // 并不知道已经重建完，会再次重建，因此需要再次判断
        log.info("获取锁成功，准备二次检查，id={}", id);
        json = stringRedisTemplate.opsForValue().get(key);
        // 过期，则需要先把json数据转为对象
        redisData = JSONUtil.toBean(json, RedisData.class);
        // 获取店铺信息
        data = JSONUtil.toBean((JSONObject) redisData.getData(), type);
        expireTime = redisData.getExpireTime();
        if (expireTime.isAfter(LocalDateTime.now())) {
            log.info("二次检查发现缓存已被重建，释放锁，id={}", id);
            // 未过期，直接返回
            unLock(lockKey);
            return data;
        }
        // 过期，开启独立线程，实现缓存重建
        // 2.redis中不存在，根据id查询数据库
        CACHE_REBUILD_EXECUTOR.submit(() -> {
            try {
                log.info("开始异步缓存重建，id={}", id);
                // 缓存重建
                T t = dbFallback.apply(id);
                this.setWithLogicExpire(key, t, time, unit);
                log.info("异步缓存重建完成，id={}", id);
            } catch (Exception e) {
                log.error("异步缓存重建失败，id={}", id, e);
                throw new RuntimeException(e);
            } finally {
                log.info("释放锁，id={}", id);
                // 释放锁
                unLock(lockKey);
            }
        });
        // 5.返回商铺信息
        return data;
    }

    /**
     * 尝试获取锁
     *
     * @param key
     * @return
     */
    private boolean trylock(String key) {
        Boolean flag = stringRedisTemplate.opsForValue()
                .setIfAbsent(key, "1", RedisConstants.CACHE_NULL_TTL, TimeUnit.MINUTES);
        return BooleanUtil.isTrue(flag);
    }

    /**
     * 释放锁
     *
     * @param key
     */
    private void unLock(String key) {
        stringRedisTemplate.delete(key);
    }


}
