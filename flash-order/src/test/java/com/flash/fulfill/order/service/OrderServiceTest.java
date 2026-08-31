package com.flash.fulfill.order.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flash.fulfill.common.constant.OrderStatus;
import com.flash.fulfill.common.dto.DeductStockCommand;
import com.flash.fulfill.common.dto.FlashOrderView;
import com.flash.fulfill.common.dto.OrderFulfillEvent;
import com.flash.fulfill.common.dto.SeckillOrderCommand;
import com.flash.fulfill.order.dto.OrderConfirmCommand;
import com.flash.fulfill.order.entity.FlashOrder;
import com.flash.fulfill.order.entity.OrderAddress;
import com.flash.fulfill.order.entity.OrderOutbox;
import com.flash.fulfill.order.mapper.OrderAddressMapper;
import com.flash.fulfill.order.mapper.OrderMapper;
import com.flash.fulfill.order.mapper.OrderOutboxMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OrderServiceTest {

    private OrderMapper orderMapper;
    private OrderOutboxMapper orderOutboxMapper;
    private OrderAddressMapper orderAddressMapper;
    private ObjectMapper objectMapper;
    private OrderService service;

    @BeforeEach
    void setUp() {
        orderMapper = mock(OrderMapper.class);
        orderOutboxMapper = mock(OrderOutboxMapper.class);
        orderAddressMapper = mock(OrderAddressMapper.class);
        objectMapper = new ObjectMapper();
        service = new OrderService(orderMapper, orderOutboxMapper, orderAddressMapper, objectMapper);
    }

    private SeckillOrderCommand buildCommand(BigDecimal price) {
        SeckillOrderCommand cmd = new SeckillOrderCommand();
        cmd.setRequestId("req-001");
        cmd.setUserId(1001L);
        cmd.setSkuId(1001L);
        cmd.setSpuId(2001L);
        cmd.setActivityId(1L);
        cmd.setQuantity(2);
        cmd.setPrice(price);
        return cmd;
    }

    private FlashOrder buildOrder(String orderNo, String status) {
        FlashOrder order = new FlashOrder();
        order.setId(10L);
        order.setOrderNo(orderNo);
        order.setUserId(1001L);
        order.setSkuId(1001L);
        order.setActivityId(1L);
        order.setQuantity(2);
        order.setAmount(new BigDecimal("398.00"));
        order.setStatus(status);
        order.setRequestId("req-001");
        return order;
    }

    private OrderConfirmCommand buildConfirm() {
        OrderConfirmCommand cmd = new OrderConfirmCommand();
        cmd.setReceiverName("张三");
        cmd.setReceiverPhone("13800000000");
        cmd.setProvince("广东省");
        cmd.setCity("深圳市");
        cmd.setDistrict("南山区");
        cmd.setDetailAddress("科技园路 1 号");
        return cmd;
    }

    private OrderAddress buildAddress() {
        OrderAddress address = new OrderAddress();
        address.setId(1L);
        address.setOrderId(10L);
        address.setReceiverName("旧");
        address.setReceiverPhone("139");
        address.setProvince("旧省");
        address.setCity("旧市");
        address.setDistrict("旧区");
        address.setDetailAddress("旧地址");
        return address;
    }

    @Test
    void createsOrderAtInitialAndDeductOutboxUsingMessagePrice() throws Exception {
        List<String> insertedStatus = new ArrayList<>();
        when(orderMapper.insert(any(FlashOrder.class))).thenAnswer(inv -> {
            insertedStatus.add(((FlashOrder) inv.getArgument(0)).getStatus());
            return 1;
        });
        List<OrderOutbox> insertedOutbox = new ArrayList<>();
        when(orderOutboxMapper.insert(any(OrderOutbox.class))).thenAnswer(inv -> {
            insertedOutbox.add((OrderOutbox) inv.getArgument(0));
            return 1;
        });

        service.handleOrderCreate(buildCommand(new BigDecimal("199.00")));

        assertEquals(List.of(OrderStatus.INITIAL), insertedStatus);

        ArgumentCaptor<FlashOrder> orderCaptor = ArgumentCaptor.forClass(FlashOrder.class);
        verify(orderMapper).insert(orderCaptor.capture());
        FlashOrder order = orderCaptor.getValue();
        assertEquals("req-001", order.getRequestId());
        assertEquals(0, new BigDecimal("398.00").compareTo(order.getAmount()));
        assertEquals(OrderStatus.INITIAL, order.getStatus());
        verify(orderAddressMapper, never()).insert(any(OrderAddress.class));

        assertEquals(1, insertedOutbox.size());
        OrderOutbox outbox = insertedOutbox.get(0);
        assertEquals("DEDUCT", outbox.getMsgType());
        assertEquals("PENDING", outbox.getStatus());
        DeductStockCommand body = objectMapper.readValue(outbox.getMsgBody(), DeductStockCommand.class);
        assertEquals("req-001", body.getRequestId());
        assertEquals(1001L, body.getSkuId());
        assertEquals(2, body.getQuantity());
    }

    @Test
    void throwsWhenMessagePriceMissing() {
        assertThrows(IllegalStateException.class, () -> service.handleOrderCreate(buildCommand(null)));

        verify(orderMapper, never()).insert(any(FlashOrder.class));
        verify(orderOutboxMapper, never()).insert(any(OrderOutbox.class));
    }

    @Test
    void queryByRequestIdReturnsInitialWhenOrderNotYetPersisted() {
        when(orderMapper.selectByRequestId("req-001")).thenReturn(null);

        FlashOrderView view = service.queryByRequestId("req-001");

        assertEquals(OrderStatus.INITIAL, view.getStatus());
        assertNull(view.getOrderNo());
    }

    @Test
    void confirmOrderInfoTransitionsInitialToPendingPaymentAndInsertsAddress() {
        when(orderMapper.selectByOrderNo("FF-O-1")).thenReturn(buildOrder("FF-O-1", OrderStatus.INITIAL));
        when(orderAddressMapper.selectByOrderId(10L)).thenReturn(null);
        List<String> updatedStatus = new ArrayList<>();
        when(orderMapper.updateById(any(FlashOrder.class))).thenAnswer(inv -> {
            updatedStatus.add(((FlashOrder) inv.getArgument(0)).getStatus());
            return 1;
        });
        List<OrderAddress> inserted = new ArrayList<>();
        when(orderAddressMapper.insert(any(OrderAddress.class))).thenAnswer(inv -> {
            inserted.add((OrderAddress) inv.getArgument(0));
            return 1;
        });

        service.confirmOrderInfo("FF-O-1", buildConfirm());

        assertEquals(List.of(OrderStatus.PENDING_PAYMENT), updatedStatus);
        assertEquals(1, inserted.size());
        OrderAddress address = inserted.get(0);
        assertEquals(10L, address.getOrderId());
        assertEquals("张三", address.getReceiverName());
        assertEquals("13800000000", address.getReceiverPhone());
        assertEquals("广东省", address.getProvince());
        assertEquals("深圳市", address.getCity());
        assertEquals("南山区", address.getDistrict());
        assertEquals("科技园路 1 号", address.getDetailAddress());
    }

    @Test
    void confirmOrderInfoUpdatesExistingAddressOnResubmit() {
        when(orderMapper.selectByOrderNo("FF-O-1")).thenReturn(buildOrder("FF-O-1", OrderStatus.PENDING_PAYMENT));
        when(orderAddressMapper.selectByOrderId(10L)).thenReturn(buildAddress());
        List<String> updatedStatus = new ArrayList<>();
        when(orderMapper.updateById(any(FlashOrder.class))).thenAnswer(inv -> {
            updatedStatus.add(((FlashOrder) inv.getArgument(0)).getStatus());
            return 1;
        });
        List<OrderAddress> updated = new ArrayList<>();
        when(orderAddressMapper.updateById(any(OrderAddress.class))).thenAnswer(inv -> {
            updated.add((OrderAddress) inv.getArgument(0));
            return 1;
        });

        service.confirmOrderInfo("FF-O-1", buildConfirm());

        assertEquals(0, updatedStatus.size());
        assertEquals(1, updated.size());
        assertEquals("张三", updated.get(0).getReceiverName());
        assertEquals("科技园路 1 号", updated.get(0).getDetailAddress());
        verify(orderAddressMapper, never()).insert(any(OrderAddress.class));
    }

    @Test
    void confirmOrderInfoIgnoresNonConfirmableStatus() {
        when(orderMapper.selectByOrderNo("FF-O-1")).thenReturn(buildOrder("FF-O-1", OrderStatus.SHIPPED));

        service.confirmOrderInfo("FF-O-1", buildConfirm());

        verify(orderMapper, never()).updateById(any(FlashOrder.class));
        verify(orderAddressMapper, never()).insert(any(OrderAddress.class));
    }

    @Test
    void markPaidTransitionsPendingPaymentToPendingShipmentAndWritesFulfillOutbox() throws Exception {
        when(orderMapper.selectByOrderNo("FF-O-1")).thenReturn(buildOrder("FF-O-1", OrderStatus.PENDING_PAYMENT));
        List<String> updated = new ArrayList<>();
        when(orderMapper.updateById(any(FlashOrder.class))).thenAnswer(inv -> {
            updated.add(((FlashOrder) inv.getArgument(0)).getStatus());
            return 1;
        });
        List<OrderOutbox> insertedOutbox = new ArrayList<>();
        when(orderOutboxMapper.insert(any(OrderOutbox.class))).thenAnswer(inv -> {
            insertedOutbox.add((OrderOutbox) inv.getArgument(0));
            return 1;
        });

        service.markPaid("FF-O-1");

        assertEquals(List.of(OrderStatus.PENDING_SHIPMENT), updated);
        assertEquals(1, insertedOutbox.size());
        assertEquals("FULFILL", insertedOutbox.get(0).getMsgType());
        assertEquals("PENDING", insertedOutbox.get(0).getStatus());
        OrderFulfillEvent event = objectMapper.readValue(insertedOutbox.get(0).getMsgBody(), OrderFulfillEvent.class);
        assertEquals("FF-O-1", event.getOrderNo());
        assertEquals(1001L, event.getUserId());
        assertEquals(1001L, event.getSkuId());
        assertEquals(2, event.getQuantity());
    }

    @Test
    void markDispatchedTransitionsPendingShipmentToShipped() {
        when(orderMapper.selectByOrderNo("FF-O-1")).thenReturn(buildOrder("FF-O-1", OrderStatus.PENDING_SHIPMENT));
        List<String> updated = new ArrayList<>();
        when(orderMapper.updateById(any(FlashOrder.class))).thenAnswer(inv -> {
            updated.add(((FlashOrder) inv.getArgument(0)).getStatus());
            return 1;
        });

        service.markDispatched("FF-O-1");

        assertEquals(List.of(OrderStatus.SHIPPED), updated);
    }

    @Test
    void markDispatchedIgnoresNonPendingShipment() {
        when(orderMapper.selectByOrderNo("FF-O-1")).thenReturn(buildOrder("FF-O-1", OrderStatus.PENDING_PAYMENT));

        service.markDispatched("FF-O-1");

        verify(orderMapper, never()).updateById(any(FlashOrder.class));
    }

    @Test
    void markCompletedTransitionsShippedToCompleted() {
        when(orderMapper.selectByOrderNo("FF-O-1")).thenReturn(buildOrder("FF-O-1", OrderStatus.SHIPPED));
        List<String> updated = new ArrayList<>();
        when(orderMapper.updateById(any(FlashOrder.class))).thenAnswer(inv -> {
            updated.add(((FlashOrder) inv.getArgument(0)).getStatus());
            return 1;
        });

        service.markCompleted("FF-O-1");

        assertEquals(List.of(OrderStatus.COMPLETED), updated);
    }

    @Test
    void cancelTransitionsPendingPaymentToCancelled() {
        when(orderMapper.selectByOrderNo("FF-O-1")).thenReturn(buildOrder("FF-O-1", OrderStatus.PENDING_PAYMENT));
        List<String> updated = new ArrayList<>();
        when(orderMapper.updateById(any(FlashOrder.class))).thenAnswer(inv -> {
            updated.add(((FlashOrder) inv.getArgument(0)).getStatus());
            return 1;
        });

        service.cancel("FF-O-1");

        assertEquals(List.of(OrderStatus.CANCELLED), updated);
    }

    @Test
    void cancelIgnoresUnknownOrTerminalOrder() {
        when(orderMapper.selectByOrderNo("missing")).thenReturn(null);

        service.cancel("missing");

        verify(orderMapper, never()).updateById(any(FlashOrder.class));
    }
}