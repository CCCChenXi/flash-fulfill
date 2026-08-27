package com.flash.fulfill.seckill.service;

import com.flash.fulfill.common.api.ErrorCode;
import com.flash.fulfill.common.api.Result;
import com.flash.fulfill.common.constant.MqTopics;
import com.flash.fulfill.common.dto.FlashOrderResponse;
import com.flash.fulfill.common.dto.SeckillOrderCommand;
import com.flash.fulfill.common.dto.SkuSellView;
import com.flash.fulfill.common.exception.BizException;
import com.flash.fulfill.seckill.deductor.StockPreDeductor;
import com.flash.fulfill.seckill.feign.ProductClient;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SeckillServiceTest {

    private StockPreDeductor stockPreDeductor;
    private RocketMQTemplate rocketMQTemplate;
    private ProductClient productClient;
    private SeckillService service;

    @BeforeEach
    void setUp() {
        stockPreDeductor = Mockito.mock(StockPreDeductor.class);
        rocketMQTemplate = Mockito.mock(RocketMQTemplate.class);
        productClient = Mockito.mock(ProductClient.class);
        service = new SeckillService(stockPreDeductor, rocketMQTemplate, productClient);
        ReflectionTestUtils.setField(service, "predeductEnabled", true);
    }

    private SeckillOrderCommand buildCommand() {
        SeckillOrderCommand cmd = new SeckillOrderCommand();
        cmd.setUserId(1001L);
        cmd.setSkuId(1001L);
        cmd.setActivityId(1L);
        cmd.setQuantity(1);
        return cmd;
    }

    private Result<SkuSellView> sellable(int skuStatus, int spuStatus) {
        SkuSellView view = new SkuSellView();
        view.setSkuId(1001L);
        view.setSpuId(1001L);
        view.setSkuName("测试商品");
        view.setSkuStatus(skuStatus);
        view.setSpuStatus(spuStatus);
        return Result.ok(view);
    }

    @Test
    void successFlowPreDeductsAndSendsMessage() {
        when(productClient.sellView(1001L)).thenReturn(sellable(1, 1));
        when(stockPreDeductor.tryPreDeduct(1001L, 1)).thenReturn(true);
        when(rocketMQTemplate.syncSend(any(String.class), any(Object.class), anyLong()))
                .thenReturn(new SendResult());

        Result<FlashOrderResponse> result = service.createFlashOrder(buildCommand());

        assertTrue(result.isSuccess());
        assertNotNull(result.getData().getRequestId());
        verify(rocketMQTemplate).syncSend(
                eq(MqTopics.FLASH_ORDER_CREATE + ":" + MqTopics.TAG_ORDER_CREATE),
                any(SeckillOrderCommand.class), eq(3000L));
    }

    @Test
    void rejectsWhenStockNotEnough() {
        when(productClient.sellView(1001L)).thenReturn(sellable(1, 1));
        when(stockPreDeductor.tryPreDeduct(1001L, 1)).thenReturn(false);

        BizException ex = assertThrows(BizException.class, () -> service.createFlashOrder(buildCommand()));
        assertEquals(ErrorCode.STOCK_NOT_ENOUGH.getCode(), ex.getCode());
        verify(rocketMQTemplate, never()).syncSend(any(String.class), any(Object.class), anyLong());
    }

    @Test
    void rollsBackPreDeductWhenSendFails() {
        when(productClient.sellView(1001L)).thenReturn(sellable(1, 1));
        when(stockPreDeductor.tryPreDeduct(1001L, 1)).thenReturn(true);
        when(rocketMQTemplate.syncSend(any(String.class), any(Object.class), anyLong()))
                .thenThrow(new RuntimeException("mq down"));

        BizException ex = assertThrows(BizException.class, () -> service.createFlashOrder(buildCommand()));
        assertEquals(ErrorCode.SYSTEM_ERROR.getCode(), ex.getCode());
        verify(stockPreDeductor).rollback(1001L, 1);
    }

    @Test
    void validatesRequiredFields() {
        SeckillOrderCommand bad = buildCommand();
        bad.setQuantity(0);

        BizException ex = assertThrows(BizException.class, () -> service.createFlashOrder(bad));
        assertEquals(ErrorCode.INVALID_PARAM.getCode(), ex.getCode());
    }

    @Test
    void rejectsSkuOffShelf() {
        when(productClient.sellView(1001L)).thenReturn(sellable(0, 1));

        BizException ex = assertThrows(BizException.class, () -> service.createFlashOrder(buildCommand()));
        assertEquals(ErrorCode.PRODUCT_OFF_SHELF.getCode(), ex.getCode());
        verify(stockPreDeductor, never()).tryPreDeduct(anyLong(), any(Integer.class));
    }

    @Test
    void rejectsSpuOffShelf() {
        when(productClient.sellView(1001L)).thenReturn(sellable(1, 0));

        BizException ex = assertThrows(BizException.class, () -> service.createFlashOrder(buildCommand()));
        assertEquals(ErrorCode.PRODUCT_OFF_SHELF.getCode(), ex.getCode());
        verify(stockPreDeductor, never()).tryPreDeduct(anyLong(), any(Integer.class));
    }

    @Test
    void rejectsNullSellViewAsNotFound() {
        when(productClient.sellView(1001L)).thenReturn(null);

        BizException ex = assertThrows(BizException.class, () -> service.createFlashOrder(buildCommand()));
        assertEquals(ErrorCode.PRODUCT_NOT_FOUND.getCode(), ex.getCode());
        verify(stockPreDeductor, never()).tryPreDeduct(anyLong(), any(Integer.class));
    }

    @Test
    void rejectsNullSellViewDataAsNotFound() {
        when(productClient.sellView(1001L)).thenReturn(Result.ok(null));

        BizException ex = assertThrows(BizException.class, () -> service.createFlashOrder(buildCommand()));
        assertEquals(ErrorCode.PRODUCT_NOT_FOUND.getCode(), ex.getCode());
        verify(stockPreDeductor, never()).tryPreDeduct(anyLong(), any(Integer.class));
    }

    @Test
    void rejectsFailedSellViewAsNotFound() {
        when(productClient.sellView(1001L)).thenReturn(Result.fail(ErrorCode.SYSTEM_ERROR));

        BizException ex = assertThrows(BizException.class, () -> service.createFlashOrder(buildCommand()));
        assertEquals(ErrorCode.PRODUCT_NOT_FOUND.getCode(), ex.getCode());
        verify(stockPreDeductor, never()).tryPreDeduct(anyLong(), any(Integer.class));
    }

    @Test
    void rejectsTransportFailureAsSystemError() {
        when(productClient.sellView(1001L)).thenThrow(new RuntimeException("feign down"));

        BizException ex = assertThrows(BizException.class, () -> service.createFlashOrder(buildCommand()));
        assertEquals(ErrorCode.SYSTEM_ERROR.getCode(), ex.getCode());
        verify(stockPreDeductor, never()).tryPreDeduct(anyLong(), any(Integer.class));
    }

    @Test
    void rejectsNullSkuStatusAsNotFound() {
        SkuSellView view = new SkuSellView();
        view.setSkuId(1001L);
        view.setSkuStatus(null);
        view.setSpuStatus(1);
        when(productClient.sellView(1001L)).thenReturn(Result.ok(view));

        BizException ex = assertThrows(BizException.class, () -> service.createFlashOrder(buildCommand()));
        assertEquals(ErrorCode.PRODUCT_NOT_FOUND.getCode(), ex.getCode());
        verify(stockPreDeductor, never()).tryPreDeduct(anyLong(), any(Integer.class));
    }
}
