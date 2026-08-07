package com.hmdp.service.impl;

import com.hmdp.dto.UserDTO;
import com.hmdp.utils.RedisIdWork;
import com.hmdp.utils.UserHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
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
        VoucherOrderServiceImpl service = new VoucherOrderServiceImpl();

        ReflectionTestUtils.setField(service, "stringRedisTemplate", stringRedisTemplate);
        ReflectionTestUtils.setField(service, "redisIdWork", redisIdWork);

        UserDTO user = new UserDTO();
        user.setId(456L);
        UserHolder.saveUser(user);

        ArgumentCaptor<Object[]> argsCaptor = ArgumentCaptor.forClass(Object[].class);
        when(stringRedisTemplate.execute(any(RedisScript.class), anyList(), argsCaptor.capture()))
                .thenReturn(0L);
        when(redisIdWork.nextId("order")).thenReturn(1L);

        service.seckillVoucher(123L);

        assertThat(argsCaptor.getValue()).containsExactly("123", "456", "1");
    }
}
