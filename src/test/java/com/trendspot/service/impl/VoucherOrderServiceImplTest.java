package com.trendspot.service.impl;

import com.trendspot.dto.UserDTO;
import com.trendspot.utils.RedisIdWork;
import com.trendspot.utils.UserHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class VoucherOrderServiceImplTest {

    @AfterEach
    void tearDown() {
        UserHolder.removeUser();
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void seckillVoucherPassesStringArgsToRedisScript() {
        StringRedisTemplate stringRedisTemplate = mock(StringRedisTemplate.class);
        RedisIdWork redisIdWork = mock(RedisIdWork.class);
        KafkaTemplate<String, Object> kafkaTemplate = mock(KafkaTemplate.class);
        VoucherOrderServiceImpl service = new VoucherOrderServiceImpl();

        ReflectionTestUtils.setField(service, "stringRedisTemplate", stringRedisTemplate);
        ReflectionTestUtils.setField(service, "redisIdWork", redisIdWork);
        ReflectionTestUtils.setField(service, "kafkaTemplate", kafkaTemplate);

        UserDTO user = new UserDTO();
        user.setId(456L);
        UserHolder.saveUser(user);

        when(stringRedisTemplate.execute(
                any(RedisScript.class), anyList(), eq("123"), eq("456")))
                .thenReturn(0L);
        when(redisIdWork.nextId("order")).thenReturn(1L);

        service.seckillVoucher(123L);

        verify(stringRedisTemplate).execute(
                any(RedisScript.class), anyList(), eq("123"), eq("456"));
    }
}
