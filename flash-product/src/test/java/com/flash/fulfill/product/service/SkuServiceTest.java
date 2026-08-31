package com.flash.fulfill.product.service;

import com.flash.fulfill.common.api.ErrorCode;
import com.flash.fulfill.common.dto.SkuSellView;
import com.flash.fulfill.common.exception.BizException;
import com.flash.fulfill.product.cache.SeckillStatusWriter;
import com.flash.fulfill.product.cache.SkuCacheService;
import com.flash.fulfill.product.dto.SkuCreateCommand;
import com.flash.fulfill.product.dto.SkuUpdateCommand;
import com.flash.fulfill.product.dto.SkuView;
import com.flash.fulfill.product.entity.Sku;
import com.flash.fulfill.product.mapper.SkuMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SkuServiceTest {

    private SkuMapper skuMapper;
    private SkuCacheService cacheService;
    private SeckillStatusWriter seckillStatusWriter;
    private SkuService service;

    @BeforeEach
    void setUp() {
        skuMapper = mock(SkuMapper.class);
        cacheService = mock(SkuCacheService.class);
        seckillStatusWriter = mock(SeckillStatusWriter.class);
        service = new SkuService(skuMapper, cacheService, seckillStatusWriter);
    }

    private SkuCreateCommand buildCreateCommand() {
        SkuCreateCommand cmd = new SkuCreateCommand();
        cmd.setSpuId(100L);
        cmd.setSkuCode("SKU001");
        cmd.setName("华为 Mate 60 Pro");
        cmd.setPrice(new BigDecimal("5999.00"));
        cmd.setImage("https://img.flashfulfill.com/mate60.png");
        cmd.setSpecs("{\"color\":\"黑\",\"storage\":\"256G\"}");
        return cmd;
    }

    @Test
    void getSellViewDelegatesToCache() {
        SkuSellView view = new SkuSellView();
        view.setSkuId(200L);
        view.setSpuId(100L);
        view.setSkuName("华为 Mate 60 Pro");
        view.setPrice(new BigDecimal("5999.00"));
        view.setSkuStatus(1);
        view.setSpuStatus(0);
        when(cacheService.getSellView(200L)).thenReturn(view);

        SkuSellView result = service.getSellView(200L);

        assertEquals(200L, result.getSkuId());
        assertEquals(100L, result.getSpuId());
        assertEquals("华为 Mate 60 Pro", result.getSkuName());
        assertEquals(new BigDecimal("5999.00"), result.getPrice());
        assertEquals(1, result.getSkuStatus());
        assertEquals(0, result.getSpuStatus());
        verify(cacheService).getSellView(200L);
    }

    @Test
    void createAndView() {
        when(skuMapper.selectBySkuCode("SKU001")).thenReturn(null);
        when(skuMapper.insert(any(Sku.class))).thenAnswer(inv -> {
            Sku s = inv.getArgument(0);
            s.setId(300L);
            return 1;
        });

        TransactionSynchronizationManager.initSynchronization();
        try {
            SkuView view = service.create(buildCreateCommand());

            ArgumentCaptor<Sku> captor = ArgumentCaptor.forClass(Sku.class);
            verify(skuMapper).insert(captor.capture());
            Sku saved = captor.getValue();
            assertEquals(100L, saved.getSpuId());
            assertEquals("SKU001", saved.getSkuCode());
            assertEquals("华为 Mate 60 Pro", saved.getName());
            assertEquals(new BigDecimal("5999.00"), saved.getPrice());
            assertEquals("{\"color\":\"黑\",\"storage\":\"256G\"}", saved.getSpecs());
            assertEquals(1, saved.getStatus());

            assertEquals(300L, view.getId());
            assertEquals("SKU001", view.getSkuCode());
            assertEquals(1, view.getStatus());
            verify(cacheService, never()).evictSku(anyLong());

            triggerAfterCommit();
            verify(cacheService).evictSku(300L);
            verify(seckillStatusWriter).skuStatus(300L, true);
            verify(seckillStatusWriter).skuPrice(300L, new BigDecimal("5999.00"));
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void createRejectsDuplicateCode() {
        when(skuMapper.selectBySkuCode("SKU001")).thenReturn(new Sku());

        BizException ex = assertThrows(BizException.class, () -> service.create(buildCreateCommand()));

        assertEquals(ErrorCode.INVALID_PARAM.getCode(), ex.getCode());
        assertEquals("SKU编码已存在", ex.getMessage());
    }

    @Test
    void getMissingThrows() {
        when(skuMapper.selectById4View(9999L)).thenReturn(null);

        BizException ex = assertThrows(BizException.class, () -> service.get(9999L));

        assertEquals(ErrorCode.PRODUCT_NOT_FOUND.getCode(), ex.getCode());
    }

    @Test
    void setStatusRejectsInvalidValue() {
        BizException ex = assertThrows(BizException.class, () -> service.setStatus(200L, 2));

        assertEquals(ErrorCode.INVALID_PARAM.getCode(), ex.getCode());
        assertEquals("status 只能为 0 或 1", ex.getMessage());
    }

    @Test
    void setStatusAndUpdateEvictCache() {
        Sku sku = new Sku();
        sku.setId(200L);
        sku.setSpuId(100L);
        sku.setName("华为 Mate 60 Pro");
        sku.setPrice(new BigDecimal("5999.00"));
        sku.setStatus(1);
        when(skuMapper.selectById4View(200L)).thenReturn(sku);

        TransactionSynchronizationManager.initSynchronization();
        try {
            service.setStatus(200L, 0);
            verify(cacheService, never()).evictSku(anyLong());

            triggerAfterCommit();
            verify(cacheService).evictSku(200L);
            verify(seckillStatusWriter).skuStatus(200L, false);
            verify(seckillStatusWriter).skuPrice(200L, new BigDecimal("5999.00"));

            TransactionSynchronizationManager.clearSynchronization();
            TransactionSynchronizationManager.initSynchronization();

            SkuUpdateCommand cmd = new SkuUpdateCommand();
            cmd.setName("新名称");
            service.update(200L, cmd);
            verify(cacheService, times(1)).evictSku(200L);

            triggerAfterCommit();
            verify(cacheService, times(2)).evictSku(200L);
            verify(seckillStatusWriter, times(2)).skuPrice(200L, new BigDecimal("5999.00"));
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    private void triggerAfterCommit() {
        TransactionSynchronizationManager.getSynchronizations()
                .forEach(TransactionSynchronization::afterCommit);
    }
}
