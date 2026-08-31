package com.flash.fulfill.inventory.service;

import com.flash.fulfill.common.dto.DeductStockCommand;
import com.flash.fulfill.common.exception.BizException;
import com.flash.fulfill.inventory.entity.Stock;
import com.flash.fulfill.inventory.entity.StockFlow;
import com.flash.fulfill.inventory.mapper.StockFlowMapper;
import com.flash.fulfill.inventory.mapper.StockMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StockServiceTest {

    private StockMapper stockMapper;
    private StockFlowMapper stockFlowMapper;
    private StockService service;

    @BeforeEach
    void setUp() {
        stockMapper = mock(StockMapper.class);
        stockFlowMapper = mock(StockFlowMapper.class);
        service = new StockService(stockMapper, stockFlowMapper);
    }

    @Test
    void processDeductSuccessWritesSuccessFlow() {
        when(stockFlowMapper.existsByRequestId("req-1")).thenReturn(false);
        when(stockMapper.deductStock(1001L, 2)).thenReturn(1);

        DeductStockCommand cmd = new DeductStockCommand("req-1", "FF-O-1", 1001L, 2);
        service.processDeduct(cmd);

        ArgumentCaptor<StockFlow> captor = ArgumentCaptor.forClass(StockFlow.class);
        verify(stockFlowMapper).insert(captor.capture());
        assertEquals("SUCCESS", captor.getValue().getResult());
        assertEquals("FF-O-1", captor.getValue().getOrderNo());
        assertEquals(1001L, captor.getValue().getSkuId());
    }

    @Test
    void processDeductInsufficientWritesFailedFlow() {
        when(stockFlowMapper.existsByRequestId("req-1")).thenReturn(false);
        when(stockMapper.deductStock(1001L, 3)).thenReturn(0);

        DeductStockCommand cmd = new DeductStockCommand("req-1", "FF-O-1", 1001L, 3);
        service.processDeduct(cmd);

        ArgumentCaptor<StockFlow> captor = ArgumentCaptor.forClass(StockFlow.class);
        verify(stockFlowMapper).insert(captor.capture());
        assertEquals("FAILED", captor.getValue().getResult());
        assertEquals(3, captor.getValue().getQuantity());
    }

    @Test
    void processDeductIgnoresAlreadyProcessedRequest() {
        when(stockFlowMapper.existsByRequestId("req-1")).thenReturn(true);

        DeductStockCommand cmd = new DeductStockCommand("req-1", "FF-O-1", 1001L, 2);
        service.processDeduct(cmd);

        verify(stockMapper, never()).deductStock(anyLong(), anyInt());
        verify(stockFlowMapper, never()).insert(any(StockFlow.class));
    }

    @Test
    void processDeductRejectsInvalidParams() {
        assertThrows(BizException.class,
                () -> service.processDeduct(new DeductStockCommand("req-1", "FF-O-1", null, 1)));
        assertThrows(BizException.class, () -> service.processDeduct(null));
    }

    @Test
    void queryReturnsStock() {
        Stock s = new Stock();
        s.setSkuId(1001L);
        when(stockMapper.selectBySkuId(1001L)).thenReturn(s);

        assertEquals(1001L, service.query(1001L).getSkuId());
    }
}