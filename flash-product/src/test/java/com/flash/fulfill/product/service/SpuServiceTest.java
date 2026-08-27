package com.flash.fulfill.product.service;

import com.flash.fulfill.common.api.ErrorCode;
import com.flash.fulfill.common.exception.BizException;
import com.flash.fulfill.product.cache.SkuCacheService;
import com.flash.fulfill.product.dto.SpuCreateCommand;
import com.flash.fulfill.product.dto.SpuUpdateCommand;
import com.flash.fulfill.product.dto.SpuView;
import com.flash.fulfill.product.entity.Spu;
import com.flash.fulfill.product.mapper.SpuMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SpuServiceTest {

    private SpuMapper spuMapper;
    private SkuCacheService cacheService;
    private SpuService service;

    @BeforeEach
    void setUp() {
        spuMapper = mock(SpuMapper.class);
        cacheService = mock(SkuCacheService.class);
        service = new SpuService(spuMapper, cacheService);
    }

    private SpuCreateCommand buildCreateCommand() {
        SpuCreateCommand cmd = new SpuCreateCommand();
        cmd.setName("华为 Mate 60 Pro");
        cmd.setCategoryId(10L);
        cmd.setBrandId(20L);
        cmd.setDescription("旗舰手机");
        cmd.setMainImage("https://img.flashfulfill.com/mate60.png");
        return cmd;
    }

    @Test
    void createAndView() {
        when(spuMapper.insert(any(Spu.class))).thenAnswer(inv -> {
            Spu s = inv.getArgument(0);
            s.setId(100L);
            return 1;
        });

        SpuView view = service.create(buildCreateCommand());

        ArgumentCaptor<Spu> captor = ArgumentCaptor.forClass(Spu.class);
        verify(spuMapper).insert(captor.capture());
        Spu saved = captor.getValue();
        assertEquals("华为 Mate 60 Pro", saved.getName());
        assertEquals(10L, saved.getCategoryId());
        assertEquals(20L, saved.getBrandId());
        assertEquals("旗舰手机", saved.getDescription());
        assertEquals("https://img.flashfulfill.com/mate60.png", saved.getMainImage());
        // 新建默认上架
        assertEquals(1, saved.getStatus());

        assertEquals(100L, view.getId());
        assertEquals("华为 Mate 60 Pro", view.getName());
        assertEquals(10L, view.getCategoryId());
        assertEquals(20L, view.getBrandId());
        assertEquals("旗舰手机", view.getDescription());
        assertEquals("https://img.flashfulfill.com/mate60.png", view.getMainImage());
        assertEquals(1, view.getStatus());
    }

    @Test
    void updateSetsFields() {
        Spu existing = new Spu();
        existing.setId(100L);
        existing.setName("旧名称");
        existing.setCategoryId(10L);
        existing.setBrandId(20L);
        existing.setStatus(1);
        when(spuMapper.selectById4View(100L)).thenReturn(existing);

        SpuUpdateCommand cmd = new SpuUpdateCommand();
        cmd.setName("新名称");
        cmd.setCategoryId(11L);
        cmd.setBrandId(21L);
        cmd.setDescription("新描述");
        cmd.setMainImage("https://img.flashfulfill.com/new.png");

        TransactionSynchronizationManager.initSynchronization();
        try {
            SpuView view = service.update(100L, cmd);

            verify(spuMapper).updateById(any(Spu.class));
            verify(cacheService, never()).evictSpu(anyLong());

            triggerAfterCommit();
            verify(cacheService).evictSpu(100L);

            assertEquals("新名称", view.getName());
            assertEquals(11L, view.getCategoryId());
            assertEquals(21L, view.getBrandId());
            assertEquals("新描述", view.getDescription());
            assertEquals("https://img.flashfulfill.com/new.png", view.getMainImage());
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void getMissingThrows() {
        when(spuMapper.selectById4View(9999L)).thenReturn(null);

        BizException ex = assertThrows(BizException.class, () -> service.get(9999L));

        assertEquals(ErrorCode.PRODUCT_NOT_FOUND.getCode(), ex.getCode());
    }

    @Test
    void onOffShelfEvictChildCache() {
        Spu spu = new Spu();
        spu.setId(100L);
        spu.setName("华为 Mate 60 Pro");
        spu.setStatus(1);
        when(spuMapper.selectById4View(100L)).thenReturn(spu);

        TransactionSynchronizationManager.initSynchronization();
        try {
            service.onShelf(100L);
            verify(cacheService, never()).evictSpu(anyLong());

            triggerAfterCommit();
            verify(cacheService).evictSpu(100L);

            TransactionSynchronizationManager.clearSynchronization();
            TransactionSynchronizationManager.initSynchronization();

            service.offShelf(100L);
            verify(cacheService, times(1)).evictSpu(100L);

            triggerAfterCommit();
            verify(cacheService, times(2)).evictSpu(100L);
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    private void triggerAfterCommit() {
        TransactionSynchronizationManager.getSynchronizations()
                .forEach(TransactionSynchronization::afterCommit);
    }
}
