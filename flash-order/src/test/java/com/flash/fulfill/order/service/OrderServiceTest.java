package com.flash.fulfill.order.service;

import com.flash.fulfill.common.api.ErrorCode;
import com.flash.fulfill.common.api.Result;
import com.flash.fulfill.common.constant.MqTopics;
import com.flash.fulfill.common.constant.OrderStatus;
import com.flash.fulfill.common.dto.DeductStockResult;
import com.flash.fulfill.common.dto.SeckillOrderCommand;
import com.flash.fulfill.common.dto.SkuSellView;
import com.flash.fulfill.common.exception.BizException;
import com.flash.fulfill.order.entity.FlashOrder;
import com.flash.fulfill.order.feign.InventoryClient;
import com.flash.fulfill.order.feign.ProductClient;
import com.flash.fulfill.order.mapper.OrderMapper;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OrderServiceTest {

    private OrderMapper orderMapper;
    private ProductClient productClient;
    private InventoryClient inventoryClient;
    private RocketMQTemplate rocketMQTemplate;
    private OrderService service;

    @BeforeEach
    void setUp() {
        orderMapper = mock(OrderMapper.class);
        productClient = mock(ProductClient.class);
        inventoryClient = mock(InventoryClient.class);
        rocketMQTemplate = mock(RocketMQTemplate.class);
        service = new OrderService(orderMapper, productClient, inventoryClient, rocketMQTemplate);
    }

    private SeckillOrderCommand buildCommand() {
        SeckillOrderCommand cmd = new SeckillOrderCommand();
        cmd.setRequestId("req-001");
        cmd.setUserId(1001L);
        cmd.setSkuId(1001L);
        cmd.setActivityId(1L);
        cmd.setQuantity(2);
        return cmd;
    }

    private SkuSellView buildSellView(BigDecimal price, int skuStatus) {
        SkuSellView view = new SkuSellView();
        view.setSkuId(1001L);
        view.setSpuId(2001L);
        view.setSkuName("测试商品");
        view.setPrice(price);
        view.setSkuStatus(skuStatus);
        view.setSpuStatus(1);
        return view;
    }

    @Test
    void createsOrderAndDispatchesFulfillmentOnDeductSuccess() {
        when(orderMapper.existsByRequestId("req-001")).thenReturn(false);
        when(productClient.sellView(1001L)).thenReturn(Result.ok(buildSellView(new BigDecimal("199.00"), 1)));
        when(inventoryClient.deduct(any())).thenReturn(Result.ok(new DeductStockResult(true, 98)));
        when(rocketMQTemplate.syncSend(any(String.class), any(Object.class), anyLong()))
                .thenReturn(null);

        List<String> insertedStatus = new ArrayList<>();
        when(orderMapper.insert(any(FlashOrder.class))).thenAnswer(inv -> {
            insertedStatus.add(((FlashOrder) inv.getArgument(0)).getStatus());
            return 1;
        });
        List<String> updatedStatus = new ArrayList<>();
        List<BigDecimal> updatedAmount = new ArrayList<>();
        when(orderMapper.updateById(any(FlashOrder.class))).thenAnswer(inv -> {
            FlashOrder o = (FlashOrder) inv.getArgument(0);
            updatedStatus.add(o.getStatus());
            updatedAmount.add(o.getAmount());
            return 1;
        });

        service.handleOrderCreate(buildCommand());

        assertEquals(List.of(OrderStatus.INITIAL), insertedStatus);
        assertEquals(List.of(OrderStatus.CREATED), updatedStatus);
        assertEquals(0, new BigDecimal("398.00").compareTo(updatedAmount.get(0)));

        verify(productClient).sellView(1001L);
        verify(rocketMQTemplate).syncSend(
                eq(MqTopics.ORDER_FULFILL + ":" + MqTopics.TAG_FULFILL),
                any(Object.class),
                anyLong());
    }

    @Test
    void marksOrderFailedWhenProductUnavailable() {
        when(orderMapper.existsByRequestId("req-001")).thenReturn(false);
        when(productClient.sellView(1001L)).thenReturn(Result.fail(ErrorCode.PRODUCT_NOT_FOUND));
        when(orderMapper.insert(any(FlashOrder.class))).thenReturn(1);
        when(orderMapper.updateById(any(FlashOrder.class))).thenReturn(1);

        ArgumentCaptor<FlashOrder> captor = ArgumentCaptor.forClass(FlashOrder.class);
        service.handleOrderCreate(buildCommand());

        verify(orderMapper).insert(any(FlashOrder.class));
        verify(orderMapper).updateById(captor.capture());
        assertEquals(OrderStatus.FAILED, captor.getValue().getStatus());
        verify(inventoryClient, never()).deduct(any());
        verify(rocketMQTemplate, never()).syncSend(anyString(), any(Object.class), anyLong());
    }

    @Test
    void marksOrderFailedWhenProductThrows() {
        when(orderMapper.existsByRequestId("req-001")).thenReturn(false);
        when(productClient.sellView(1001L)).thenThrow(new RuntimeException("product down"));
        when(orderMapper.insert(any(FlashOrder.class))).thenReturn(1);
        when(orderMapper.updateById(any(FlashOrder.class))).thenReturn(1);

        ArgumentCaptor<FlashOrder> captor = ArgumentCaptor.forClass(FlashOrder.class);
        service.handleOrderCreate(buildCommand());

        verify(orderMapper).updateById(captor.capture());
        assertEquals(OrderStatus.FAILED, captor.getValue().getStatus());
        verify(inventoryClient, never()).deduct(any());
    }

    @Test
    void marksOrderFailedWhenSkuOffShelf() {
        when(orderMapper.existsByRequestId("req-001")).thenReturn(false);
        when(productClient.sellView(1001L)).thenReturn(Result.ok(buildSellView(new BigDecimal("199.00"), 0)));
        when(orderMapper.insert(any(FlashOrder.class))).thenReturn(1);
        when(orderMapper.updateById(any(FlashOrder.class))).thenReturn(1);

        service.handleOrderCreate(buildCommand());

        verify(inventoryClient, never()).deduct(any());
        ArgumentCaptor<FlashOrder> captor = ArgumentCaptor.forClass(FlashOrder.class);
        verify(orderMapper).updateById(captor.capture());
        assertEquals(OrderStatus.FAILED, captor.getValue().getStatus());
    }

    @Test
    void marksOrderFailedWhenDeductUnavailable() {
        when(orderMapper.existsByRequestId("req-001")).thenReturn(false);
        when(productClient.sellView(1001L)).thenReturn(Result.ok(buildSellView(new BigDecimal("199.00"), 1)));
        when(inventoryClient.deduct(any())).thenReturn(Result.ok(new DeductStockResult(false, null)));
        when(orderMapper.insert(any(FlashOrder.class))).thenReturn(1);

        List<String> updatedStatus = new ArrayList<>();
        when(orderMapper.updateById(any(FlashOrder.class))).thenAnswer(inv -> {
            updatedStatus.add(((FlashOrder) inv.getArgument(0)).getStatus());
            return 1;
        });

        service.handleOrderCreate(buildCommand());

        assertEquals(List.of(OrderStatus.FAILED), updatedStatus);
        verify(rocketMQTemplate, never()).syncSend(anyString(), any(Object.class), anyLong());
    }

    @Test
    void ignoresDuplicateRequest() {
        when(orderMapper.existsByRequestId("req-001")).thenReturn(true);

        service.handleOrderCreate(buildCommand());

        verify(orderMapper, never()).insert(any(FlashOrder.class));
        verify(productClient, never()).sellView(anyLong());
        verify(inventoryClient, never()).deduct(any());
    }

    @Test
    void sendsFulfillmentEvenIfFeignCallFails() {
        when(orderMapper.existsByRequestId("req-001")).thenReturn(false);
        when(productClient.sellView(1001L)).thenReturn(Result.ok(buildSellView(new BigDecimal("199.00"), 1)));
        when(inventoryClient.deduct(any())).thenThrow(new RuntimeException("inventory down"));
        when(orderMapper.insert(any(FlashOrder.class))).thenReturn(1);

        service.handleOrderCreate(buildCommand());

        ArgumentCaptor<FlashOrder> captor = ArgumentCaptor.forClass(FlashOrder.class);
        verify(orderMapper).updateById(captor.capture());
        assertEquals(OrderStatus.FAILED, captor.getValue().getStatus());
    }

    @Test
    void markDispatchedTransitionsCreatedToDispatched() {
        FlashOrder order = new FlashOrder();
        order.setStatus(OrderStatus.CREATED);
        when(orderMapper.selectByOrderNo("FF-O-1")).thenReturn(order);

        service.markDispatched("FF-O-1");

        ArgumentCaptor<FlashOrder> captor = ArgumentCaptor.forClass(FlashOrder.class);
        verify(orderMapper).updateById(captor.capture());
        assertEquals(OrderStatus.DISPATCHED, captor.getValue().getStatus());
    }

    @Test
    void queryByRequestIdThrowsWhenMissing() {
        when(orderMapper.selectByRequestId("missing")).thenReturn(null);
        BizException ex = org.junit.jupiter.api.Assertions.assertThrows(
                BizException.class, () -> service.queryByRequestId("missing"));
        assertEquals(ErrorCode.ORDER_NOT_FOUND.getCode(), ex.getCode());
    }
}
