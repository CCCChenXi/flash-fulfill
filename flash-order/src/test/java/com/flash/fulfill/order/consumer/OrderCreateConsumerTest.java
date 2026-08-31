package com.flash.fulfill.order.consumer;

import com.flash.fulfill.common.dto.SeckillOrderCommand;
import com.flash.fulfill.order.service.OrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class OrderCreateConsumerTest {

    private OrderService orderService;
    private OrderCreateConsumer consumer;

    @BeforeEach
    void setUp() {
        orderService = mock(OrderService.class);
        consumer = new OrderCreateConsumer(orderService);
    }

    private SeckillOrderCommand buildCommand() {
        SeckillOrderCommand cmd = new SeckillOrderCommand();
        cmd.setRequestId("req-001");
        cmd.setUserId(1001L);
        cmd.setSkuId(1001L);
        cmd.setSpuId(2001L);
        cmd.setQuantity(1);
        return cmd;
    }

    @Test
    void delegatesToOrderService() {
        SeckillOrderCommand cmd = buildCommand();
        consumer.onMessage(cmd);
        verify(orderService).handleOrderCreate(cmd);
    }

    @Test
    void ignoresDuplicateKeyAsAlreadyProcessed() {
        doThrow(new DuplicateKeyException("dup")).when(orderService).handleOrderCreate(buildCommand());
        assertDoesNotThrow(() -> consumer.onMessage(buildCommand()));
    }

    @Test
    void rethrowsOtherExceptionsForMqRetry() {
        doThrow(new RuntimeException("boom")).when(orderService).handleOrderCreate(buildCommand());
        assertThrows(RuntimeException.class, () -> consumer.onMessage(buildCommand()));
    }
}