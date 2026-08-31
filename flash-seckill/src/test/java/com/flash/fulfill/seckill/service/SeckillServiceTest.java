package com.flash.fulfill.seckill.service;

import com.flash.fulfill.common.api.ErrorCode;
import com.flash.fulfill.common.api.Result;
import com.flash.fulfill.common.constant.SeckillResultCode;
import com.flash.fulfill.common.dto.FlashOrderResponse;
import com.flash.fulfill.common.dto.SeckillOrderCommand;
import com.flash.fulfill.common.exception.BizException;
import com.flash.fulfill.seckill.script.SeckillScriptExecutor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SeckillServiceTest {

    private SeckillScriptExecutor scriptExecutor;
    private SeckillService service;

    @BeforeEach
    void setUp() {
        scriptExecutor = Mockito.mock(SeckillScriptExecutor.class);
        service = new SeckillService(scriptExecutor);
        ReflectionTestUtils.setField(service, "buyLimit", 1);
        ReflectionTestUtils.setField(service, "reqTtlSeconds", 600);
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

    private void stubScript(int code) {
        when(scriptExecutor.execute(any(SeckillOrderCommand.class), anyInt(), anyInt())).thenReturn(code);
    }

    @Test
    void successFlowAcceptsWithoutSendingMq() {
        stubScript(SeckillResultCode.SUCCESS);

        Result<FlashOrderResponse> result = service.createFlashOrder(buildCommand());

        assertTrue(result.isSuccess());
        assertEquals(SeckillResultCode.SUCCESS, result.getData().getStatus());
        assertNotNull(result.getData().getRequestId());
        verify(scriptExecutor).execute(any(SeckillOrderCommand.class), anyInt(), anyInt());
    }

    @Test
    void offShelfReturnsStatus2() {
        stubScript(SeckillResultCode.OFF_SHELF);
        Result<FlashOrderResponse> result = service.createFlashOrder(buildCommand());
        assertEquals(SeckillResultCode.OFF_SHELF, result.getData().getStatus());
    }

    @Test
    void stockNotEnoughReturnsStatus3() {
        stubScript(SeckillResultCode.STOCK_NOT_ENOUGH);
        Result<FlashOrderResponse> result = service.createFlashOrder(buildCommand());
        assertEquals(SeckillResultCode.STOCK_NOT_ENOUGH, result.getData().getStatus());
    }

    @Test
    void processingReturnsStatus5() {
        stubScript(SeckillResultCode.PROCESSING);
        Result<FlashOrderResponse> result = service.createFlashOrder(buildCommand());
        assertEquals(SeckillResultCode.PROCESSING, result.getData().getStatus());
    }

    @Test
    void limitReturnsStatus6() {
        stubScript(SeckillResultCode.LIMIT);
        Result<FlashOrderResponse> result = service.createFlashOrder(buildCommand());
        assertEquals(SeckillResultCode.LIMIT, result.getData().getStatus());
    }

    @Test
    void priceUnavailableReturnsStatus7() {
        stubScript(SeckillResultCode.PRICE_UNAVAILABLE);
        Result<FlashOrderResponse> result = service.createFlashOrder(buildCommand());
        assertEquals(SeckillResultCode.PRICE_UNAVAILABLE, result.getData().getStatus());
    }

    @Test
    void scriptErrorReturnsSystemError() {
        stubScript(-1);
        BizException ex = assertThrows(BizException.class, () -> service.createFlashOrder(buildCommand()));
        assertEquals(ErrorCode.SYSTEM_ERROR.getCode(), ex.getCode());
    }
}