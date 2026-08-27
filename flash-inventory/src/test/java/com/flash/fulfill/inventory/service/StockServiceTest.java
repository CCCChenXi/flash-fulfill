package com.flash.fulfill.inventory.service;

import com.flash.fulfill.common.dto.DeductStockCommand;
import com.flash.fulfill.common.dto.DeductStockResult;
import com.flash.fulfill.common.exception.BizException;
import com.flash.fulfill.inventory.entity.Stock;
import com.flash.fulfill.inventory.mapper.StockMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StockServiceTest {

    private StockMapper stockMapper;
    private StockService service;

    @BeforeEach
    void setUp() {
        stockMapper = mock(StockMapper.class);
        service = new StockService(stockMapper);
    }

    @Test
    void deductSuccessReturnsRemaining() {
        when(stockMapper.deductStock(1001L, 2)).thenReturn(1);
        Stock after = new Stock();
        after.setAvailable(98);
        when(stockMapper.selectBySkuId(1001L)).thenReturn(after);

        DeductStockCommand cmd = new DeductStockCommand("req-1", "FF-O-1", 1001L, 2);
        DeductStockResult result = service.deduct(cmd);

        assertTrue(result.isSuccess());
        assertEquals(98, result.getAvailableAfter());
        verify(stockMapper).deductStock(1001L, 2);
    }

    @Test
    void deductFailsWhenNotEnough() {
        when(stockMapper.deductStock(1001L, 3)).thenReturn(0);

        DeductStockCommand cmd = new DeductStockCommand("req-1", "FF-O-1", 1001L, 3);
        DeductStockResult result = service.deduct(cmd);

        assertFalse(result.isSuccess());
    }

    @Test
    void rejectInvalidParams() {
        DeductStockCommand bad = new DeductStockCommand("req-1", "FF-O-1", null, 1);
        assertThrows(BizException.class, () -> service.deduct(bad));
        assertThrows(BizException.class, () -> service.deduct(null));
    }
}
