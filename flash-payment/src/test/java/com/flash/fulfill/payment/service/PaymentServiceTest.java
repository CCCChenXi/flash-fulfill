package com.flash.fulfill.payment.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flash.fulfill.common.exception.BizException;
import com.flash.fulfill.payment.constant.PaymentStatus;
import com.flash.fulfill.payment.dto.PaymentCallbackCommand;
import com.flash.fulfill.payment.dto.PaymentCreateCommand;
import com.flash.fulfill.payment.dto.PaymentView;
import com.flash.fulfill.payment.entity.PaymentOutbox;
import com.flash.fulfill.payment.entity.PaymentRecord;
import com.flash.fulfill.payment.mapper.PaymentOutboxMapper;
import com.flash.fulfill.payment.mapper.PaymentRecordMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PaymentServiceTest {

    private PaymentRecordMapper paymentRecordMapper;
    private PaymentOutboxMapper paymentOutboxMapper;
    private ObjectMapper objectMapper;
    private PaymentService service;

    @BeforeEach
    void setUp() {
        paymentRecordMapper = mock(PaymentRecordMapper.class);
        paymentOutboxMapper = mock(PaymentOutboxMapper.class);
        objectMapper = new ObjectMapper();
        service = new PaymentService(paymentRecordMapper, paymentOutboxMapper, objectMapper);
    }

    private PaymentCreateCommand buildCreate() {
        PaymentCreateCommand cmd = new PaymentCreateCommand();
        cmd.setOrderNo("FF-O-1");
        cmd.setUserId(1001L);
        cmd.setAmount(new BigDecimal("398.00"));
        cmd.setChannel("MOCK");
        return cmd;
    }

    private PaymentRecord buildRecord(String payNo, String orderNo, String status) {
        PaymentRecord r = new PaymentRecord();
        r.setId(1L);
        r.setPayNo(payNo);
        r.setOrderNo(orderNo);
        r.setUserId(1001L);
        r.setAmount(new BigDecimal("398.00"));
        r.setChannel("MOCK");
        r.setStatus(status);
        return r;
    }

    @Test
    void createsInitialPaymentWithGeneratedPayNo() {
        when(paymentRecordMapper.selectByOrderNo("FF-O-1")).thenReturn(null);
        List<String> statuses = new ArrayList<>();
        when(paymentRecordMapper.insert(any(PaymentRecord.class))).thenAnswer(inv -> {
            statuses.add(((PaymentRecord) inv.getArgument(0)).getStatus());
            return 1;
        });

        PaymentView view = service.createPayment(buildCreate());

        assertEquals(PaymentStatus.INITIAL, view.getStatus());
        assertEquals("FF-O-1", view.getOrderNo());
        assertEquals(0, new BigDecimal("398.00").compareTo(view.getAmount()));
        assertEquals("MOCK", view.getChannel());
        assertEquals(PaymentStatus.INITIAL, statuses.get(0));
    }

    @Test
    void returnsExistingPaymentWhenOrderAlreadyHasOne() {
        when(paymentRecordMapper.selectByOrderNo("FF-O-1"))
                .thenReturn(buildRecord("PAY-1", "FF-O-1", PaymentStatus.PAID));

        PaymentView view = service.createPayment(buildCreate());

        assertEquals("PAY-1", view.getPayNo());
        assertEquals(PaymentStatus.PAID, view.getStatus());
        verify(paymentRecordMapper, never()).insert(any(PaymentRecord.class));
    }

    @Test
    void callbackMarksPaidAndWritesOutbox() throws Exception {
        when(paymentRecordMapper.selectByPayNo("PAY-1"))
                .thenReturn(buildRecord("PAY-1", "FF-O-1", PaymentStatus.INITIAL));
        List<String> updated = new ArrayList<>();
        when(paymentRecordMapper.updateById(any(PaymentRecord.class))).thenAnswer(inv -> {
            updated.add(((PaymentRecord) inv.getArgument(0)).getStatus());
            return 1;
        });
        List<PaymentOutbox> inserted = new ArrayList<>();
        when(paymentOutboxMapper.insert(any(PaymentOutbox.class))).thenAnswer(inv -> {
            inserted.add((PaymentOutbox) inv.getArgument(0));
            return 1;
        });

        PaymentCallbackCommand cmd = new PaymentCallbackCommand();
        cmd.setPayNo("PAY-1");
        cmd.setSuccess(true);
        PaymentView view = service.handleCallback(cmd);

        assertEquals(PaymentStatus.PAID, view.getStatus());
        assertEquals(List.of(PaymentStatus.PAID), updated);
        assertEquals(1, inserted.size());
        assertEquals("MARK_PAID", inserted.get(0).getMsgType());
        assertEquals("PENDING", inserted.get(0).getStatus());
        assertEquals("FF-O-1", objectMapper.readTree(inserted.get(0).getMsgBody()).get("orderNo").asText());
    }

    @Test
    void callbackIgnoresAlreadyPaid() {
        when(paymentRecordMapper.selectByPayNo("PAY-1"))
                .thenReturn(buildRecord("PAY-1", "FF-O-1", PaymentStatus.PAID));

        PaymentCallbackCommand cmd = new PaymentCallbackCommand();
        cmd.setPayNo("PAY-1");
        cmd.setSuccess(true);
        PaymentView view = service.handleCallback(cmd);

        assertEquals(PaymentStatus.PAID, view.getStatus());
        verify(paymentRecordMapper, never()).updateById(any(PaymentRecord.class));
        verify(paymentOutboxMapper, never()).insert(any(PaymentOutbox.class));
    }

    @Test
    void callbackMarksFailedWithoutOutbox() {
        when(paymentRecordMapper.selectByPayNo("PAY-1"))
                .thenReturn(buildRecord("PAY-1", "FF-O-1", PaymentStatus.INITIAL));
        List<String> updated = new ArrayList<>();
        when(paymentRecordMapper.updateById(any(PaymentRecord.class))).thenAnswer(inv -> {
            updated.add(((PaymentRecord) inv.getArgument(0)).getStatus());
            return 1;
        });

        PaymentCallbackCommand cmd = new PaymentCallbackCommand();
        cmd.setPayNo("PAY-1");
        cmd.setSuccess(false);
        PaymentView view = service.handleCallback(cmd);

        assertEquals(PaymentStatus.FAILED, view.getStatus());
        assertEquals(List.of(PaymentStatus.FAILED), updated);
        verify(paymentOutboxMapper, never()).insert(any(PaymentOutbox.class));
    }

    @Test
    void callbackUnknownPayNoThrows() {
        when(paymentRecordMapper.selectByPayNo("PAY-X")).thenReturn(null);

        PaymentCallbackCommand cmd = new PaymentCallbackCommand();
        cmd.setPayNo("PAY-X");
        cmd.setSuccess(true);

        assertThrows(BizException.class, () -> service.handleCallback(cmd));
    }
}