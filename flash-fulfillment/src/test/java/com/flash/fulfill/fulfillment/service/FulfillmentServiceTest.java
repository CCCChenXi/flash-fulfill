package com.flash.fulfill.fulfillment.service;

import com.flash.fulfill.common.api.Result;
import com.flash.fulfill.common.dto.OrderFulfillEvent;
import com.flash.fulfill.fulfillment.constant.FulfillmentConstant;
import com.flash.fulfill.fulfillment.entity.DispatchRecord;
import com.flash.fulfill.fulfillment.feign.OrderClient;
import com.flash.fulfill.fulfillment.mapper.DispatchRecordMapper;
import com.flash.fulfill.fulfillment.router.DefaultWarehouseRouter;
import com.flash.fulfill.fulfillment.router.WarehouseRouter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FulfillmentServiceTest {

    private DispatchRecordMapper dispatchRecordMapper;
    private OrderClient orderClient;
    private FulfillmentService service;

    @BeforeEach
    void setUp() {
        dispatchRecordMapper = mock(DispatchRecordMapper.class);
        WarehouseRouter router = new DefaultWarehouseRouter();
        orderClient = mock(OrderClient.class);
        service = new FulfillmentService(dispatchRecordMapper, router, orderClient);
    }

    private OrderFulfillEvent buildEvent() {
        OrderFulfillEvent event = new OrderFulfillEvent();
        event.setOrderNo("FF1234567890");
        event.setUserId(1001L);
        event.setSkuId(1001L);
        event.setQuantity(2);
        return event;
    }

    @Test
    void dispatchCreatesRecordAndNotifiesOrder() {
        when(dispatchRecordMapper.existsByOrderNo("FF1234567890")).thenReturn(false);
        when(orderClient.markDispatched("FF1234567890")).thenReturn(Result.ok(true));

        service.dispatch(buildEvent());

        ArgumentCaptor<DispatchRecord> captor = ArgumentCaptor.forClass(DispatchRecord.class);
        verify(dispatchRecordMapper).insert(captor.capture());
        DispatchRecord saved = captor.getValue();
        assertEquals("FF1234567890", saved.getOrderNo());
        assertNotNull(saved.getWarehouseCode());
        assertEquals(FulfillmentConstant.STATUS_DISPATCHED, saved.getStatus());
        assertNotNull(saved.getTrackingNo());

        verify(orderClient).markDispatched("FF1234567890");
    }

    @Test
    void ignoresDuplicateOrder() {
        when(dispatchRecordMapper.existsByOrderNo("FF1234567890")).thenReturn(true);

        service.dispatch(buildEvent());

        verify(dispatchRecordMapper, never()).insert(any(DispatchRecord.class));
    }

    @Test
    void dispatchStillCompletesWhenOrderCallbackFails() {
        when(dispatchRecordMapper.existsByOrderNo("FF1234567890")).thenReturn(false);
        when(orderClient.markDispatched(anyString())).thenThrow(new RuntimeException("order down"));

        service.dispatch(buildEvent());

        verify(dispatchRecordMapper).insert(any(DispatchRecord.class));
    }

    @Test
    void defaultRouterAlwaysReturnsWarehouse() {
        DefaultWarehouseRouter router = new DefaultWarehouseRouter();
        assertNotNull(router.route(1001L, 1001L));
        assertNotNull(router.route(1002L, 1002L));
        assertNotNull(router.route(7L, 9L));
    }
}

