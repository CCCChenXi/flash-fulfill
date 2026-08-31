package com.flash.fulfill.payment.service;

import com.flash.fulfill.common.api.ErrorCode;
import com.flash.fulfill.common.api.Result;
import com.flash.fulfill.payment.entity.PaymentOutbox;
import com.flash.fulfill.payment.feign.OrderClient;
import com.flash.fulfill.payment.mapper.PaymentOutboxMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PaymentOutboxRelayTest {

    private PaymentOutboxMapper outboxMapper;
    private OrderClient orderClient;
    private PaymentOutboxRelay relay;

    @BeforeEach
    void setUp() {
        outboxMapper = mock(PaymentOutboxMapper.class);
        orderClient = mock(OrderClient.class);
        relay = new PaymentOutboxRelay(outboxMapper, orderClient);
    }

    private PaymentOutbox buildRecord(Long id, String orderNo) {
        PaymentOutbox o = new PaymentOutbox();
        o.setId(id);
        o.setPayNo("PAY-1");
        o.setOrderNo(orderNo);
        o.setMsgType("MARK_PAID");
        o.setMsgBody("{\"orderNo\":\"" + orderNo + "\"}");
        return o;
    }

    @Test
    void marksSentWhenOrderAccepts() {
        when(outboxMapper.selectPending(anyInt())).thenReturn(List.of(buildRecord(1L, "FF-O-1")));
        when(orderClient.markPaid("FF-O-1")).thenReturn(Result.ok(true));

        relay.relayPending();

        verify(outboxMapper).markSent(1L);
        verify(outboxMapper, never()).incrementRetry(1L);
    }

    @Test
    void incrementsRetryWhenOrderRejects() {
        when(outboxMapper.selectPending(anyInt())).thenReturn(List.of(buildRecord(1L, "FF-O-1")));
        when(orderClient.markPaid("FF-O-1")).thenReturn(Result.fail(ErrorCode.SYSTEM_ERROR));

        relay.relayPending();

        verify(outboxMapper).incrementRetry(1L);
        verify(outboxMapper, never()).markSent(1L);
    }

    @Test
    void incrementsRetryWhenFeignThrows() {
        when(outboxMapper.selectPending(anyInt())).thenReturn(List.of(buildRecord(1L, "FF-O-1")));
        when(orderClient.markPaid("FF-O-1")).thenThrow(new RuntimeException("order down"));

        relay.relayPending();

        verify(outboxMapper).incrementRetry(1L);
        verify(outboxMapper, never()).markSent(1L);
    }
}