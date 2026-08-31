package com.flash.fulfill.inventory.consumer;

import com.flash.fulfill.common.dto.DeductStockCommand;
import com.flash.fulfill.inventory.service.StockService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class StockDeductConsumerTest {

    private StockService stockService;
    private StockDeductConsumer consumer;

    @BeforeEach
    void setUp() {
        stockService = mock(StockService.class);
        consumer = new StockDeductConsumer(stockService);
    }

    @Test
    void delegatesToStockService() {
        DeductStockCommand cmd = new DeductStockCommand("req-1", "FF-O-1", 1001L, 2);
        consumer.onMessage(cmd);
        verify(stockService).processDeduct(cmd);
    }

    @Test
    void ignoresDuplicateKeyAsAlreadyProcessed() {
        doThrow(new DuplicateKeyException("dup")).when(stockService).processDeduct(any());
        assertDoesNotThrow(() -> consumer.onMessage(new DeductStockCommand("req-1", "FF-O-1", 1001L, 2)));
    }

    @Test
    void rethrowsOtherExceptionsForMqRetry() {
        doThrow(new RuntimeException("boom")).when(stockService).processDeduct(any());
        assertThrows(RuntimeException.class,
                () -> consumer.onMessage(new DeductStockCommand("req-1", "FF-O-1", 1001L, 2)));
    }
}