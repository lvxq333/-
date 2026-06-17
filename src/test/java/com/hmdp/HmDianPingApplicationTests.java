package com.hmdp;

import com.hmdp.entity.Shop;
import com.hmdp.service.impl.ShopServiceImpl;
import com.hmdp.utils.CacheClient;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RedisIdWork;
import io.lettuce.core.Value;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import javax.annotation.Resource;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@SpringBootTest
class HmDianPingApplicationTests {

    @Resource
    private ShopServiceImpl shopService;
    @Resource
    private CacheClient cacheClient;
    @Resource
    private RedisIdWork redisIdWorker;
    // 线程池
    private ExecutorService es = Executors.newFixedThreadPool(500);

    @Test
    void testIdWorker() throws InterruptedException {
        // 创建一个计数器，线程执行完毕后，计数器减1
        CountDownLatch latch = new CountDownLatch(300);
        // 定义一个可以被线程执行的任务
        Runnable task = () -> {
            try {
                for (int i = 0; i < 100; i++) {
                    long id = redisIdWorker.nextId("order");
                    System.out.println("id = " + id);
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            } finally {
                // 线程执行完毕，计数器减1
                latch.countDown();
            }
        };
        long begin = System.currentTimeMillis();
        for (int i = 0; i < 300; i++) {
            es.submit(task);
        }
        // 等待所有线程执行完毕
        latch.await();
        long end = System.currentTimeMillis();
        System.out.println("time = " + (end - begin));
    }

    /**
     * 向redis中写入商铺数据
     */
    @Test
    void testSaveShop() {
        for (int i = 1; i <= 14; i++) {
            Shop shop = shopService.getById((long)i);
            cacheClient.setWithLogicExpire(
                    RedisConstants.CACHE_SHOP_KEY + (long)i, shop, 10L, TimeUnit.SECONDS);
        }
    }

}
