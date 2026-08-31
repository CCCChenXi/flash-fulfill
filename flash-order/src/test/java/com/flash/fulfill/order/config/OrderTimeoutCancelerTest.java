package com.flash.fulfill.order.config;

import com.flash.fulfill.common.constant.OrderStatus;
import com.flash.fulfill.order.entity.FlashOrder;
import com.flash.fulfill.order.mapper.OrderMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OrderTimeoutCancelerTest {

    private OrderMapper orderMapper;
    private OrderTimeoutCanceler canceller;

    @BeforeEach
    void setUp() {
        orderMapper = mock(OrderMapper.class);
        canceller = new OrderTimeoutCanceler(orderMapper);
    }

    private FlashOrder buildOrder(Long id, String orderNo) {
        FlashOrder order = new FlashOrder();
        order.setId(id);
        order.setOrderNo(orderNo);
        order.setStatus(OrderStatus.PENDING_PAYMENT);
        return order;
    }

    @Test
    void cancelsExpiredPendingOrders() {
        when(orderMapper.selectExpiredPending(any(), anyInt()))
                .thenReturn(List.of(buildOrder(1L, "FF-1"), buildOrder(2L, "FF-2")));

        canceller.cancelExpired();

        ArgumentCaptor<FlashOrder> captor = ArgumentCaptor.forClass(FlashOrder.class);
        verify(orderMapper, org.mockito.Mockito.times(2)).updateById(captor.capture());
        for (FlashOrder order : captor.getAllValues()) {
            assertEquals(OrderStatus.CANCELLED, order.getStatus());
        }
    }

    @Test
    void doesNothingWhenNoExpiredOrders() {
        when(orderMapper.selectExpiredPending(any(), anyInt())).thenReturn(List.of());

        canceller.cancelExpired();

        verify(orderMapper, never()).updateById(any(FlashOrder.class));
    }

    @Test
    void autoCompletesExpiredShippedOrders() {
        FlashOrder shipped = buildOrder(3L, "FF-3");
        shipped.setStatus(OrderStatus.SHIPPED);
        when(orderMapper.selectExpiredShipped(any(), anyInt())).thenReturn(List.of(shipped));

        canceller.autoCompleteShipped();

        ArgumentCaptor<FlashOrder> captor = ArgumentCaptor.forClass(FlashOrder.class);
        verify(orderMapper).updateById(captor.capture());
        assertEquals(OrderStatus.COMPLETED, captor.getValue().getStatus());
    }

    @Test
    void autoCompleteDoesNothingWhenNoExpiredShipped() {
        when(orderMapper.selectExpiredShipped(any(), anyInt())).thenReturn(List.of());

        canceller.autoCompleteShipped();

        verify(orderMapper, never()).updateById(any(FlashOrder.class));
    }
}