package com.flash.fulfill.seckill.script;

import com.flash.fulfill.common.constant.RedisKeys;
import com.flash.fulfill.common.dto.SeckillOrderCommand;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SeckillScriptExecutorTest {

    private StringRedisTemplate redisTemplate;
    private SeckillScriptExecutor executor;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        redisTemplate = mock(StringRedisTemplate.class);
        executor = new SeckillScriptExecutor(redisTemplate);
    }

    private SeckillOrderCommand buildCommand() {
        SeckillOrderCommand cmd = new SeckillOrderCommand();
        cmd.setRequestId("req-001");
        cmd.setUserId(1001L);
        cmd.setSkuId(1001L);
        cmd.setSpuId(1L);
        cmd.setActivityId(1L);
        cmd.setQuantity(1);
        return cmd;
    }

    private void stubScriptResult(Long value) {
        when(redisTemplate.execute(any(RedisScript.class), anyList(), any(Object[].class))).thenReturn(value);
    }

    @Test
    void successReturnsZero() {
        stubScriptResult(0L);
        assertEquals(0, executor.execute(buildCommand(), 1, 600));
    }

    @Test
    void offShelfReturnsTwo() {
        stubScriptResult(2L);
        assertEquals(2, executor.execute(buildCommand(), 1, 600));
    }

    @Test
    void stockNotEnoughReturnsThree() {
        stubScriptResult(3L);
        assertEquals(3, executor.execute(buildCommand(), 1, 600));
    }

    @Test
    void notExistReturnsFour() {
        stubScriptResult(4L);
        assertEquals(4, executor.execute(buildCommand(), 1, 600));
    }

    @Test
    void processingReturnsFive() {
        stubScriptResult(5L);
        assertEquals(5, executor.execute(buildCommand(), 1, 600));
    }

    @Test
    void limitReturnsSix() {
        stubScriptResult(6L);
        assertEquals(6, executor.execute(buildCommand(), 1, 600));
    }

    @Test
    void priceUnavailableReturnsSeven() {
        stubScriptResult(7L);
        assertEquals(7, executor.execute(buildCommand(), 1, 600));
    }

    @Test
    void nullResultReturnsMinusOne() {
        stubScriptResult(null);
        assertEquals(-1, executor.execute(buildCommand(), 1, 600));
    }

    @Test
    void passesPriceKeyAndFlatFieldArgs() {
        stubScriptResult(0L);

        executor.execute(buildCommand(), 1, 600);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<String>> keysCaptor = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<Object[]> argsCaptor = ArgumentCaptor.forClass(Object[].class);
        verify(redisTemplate).execute(any(RedisScript.class), keysCaptor.capture(), argsCaptor.capture());

        List<String> keys = keysCaptor.getValue();
        assertEquals(7, keys.size());
        assertTrue(keys.contains(RedisKeys.SECKILL_REQ_PREFIX + "req-001"));
        assertTrue(keys.contains(RedisKeys.SECKILL_PRICE_PREFIX + "1001"));
        assertTrue(keys.contains(RedisKeys.SECKILL_EVENT_STREAM));

        Object[] args = argsCaptor.getValue();
        assertEquals("1", args[0]);
        assertEquals("1", args[1]);
        assertEquals("600", args[2]);
        assertEquals(List.of(
                "requestId", "req-001",
                "userId", "1001",
                "skuId", "1001",
                "spuId", "1",
                "activityId", "1",
                "quantity", "1"), List.of(args).subList(3, args.length));
    }
}