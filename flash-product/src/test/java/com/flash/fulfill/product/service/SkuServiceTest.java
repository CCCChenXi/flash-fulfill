package com.flash.fulfill.product.service;

import com.flash.fulfill.common.api.ErrorCode;
import com.flash.fulfill.common.dto.SkuSellView;
import com.flash.fulfill.common.exception.BizException;
import com.flash.fulfill.product.dto.SkuCreateCommand;
import com.flash.fulfill.product.dto.SkuView;
import com.flash.fulfill.product.entity.Sku;
import com.flash.fulfill.product.entity.Spu;
import com.flash.fulfill.product.mapper.SkuMapper;
import com.flash.fulfill.product.mapper.SpuMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SkuServiceTest {

    private SkuMapper skuMapper;
    private SpuMapper spuMapper;
    private SkuService service;

    @BeforeEach
    void setUp() {
        skuMapper = mock(SkuMapper.class);
        spuMapper = mock(SpuMapper.class);
        service = new SkuService(skuMapper, spuMapper);
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
    void getSellViewMergesParentStatus() {
        Sku sku = new Sku();
        sku.setId(200L);
        sku.setSpuId(100L);
        sku.setSkuCode("SKU001");
        sku.setName("华为 Mate 60 Pro");
        sku.setPrice(new BigDecimal("5999.00"));
        sku.setStatus(1);
        when(skuMapper.selectById4View(200L)).thenReturn(sku);

        Spu spu = new Spu();
        spu.setId(100L);
        spu.setStatus(0);
        when(spuMapper.selectById4View(100L)).thenReturn(spu);

        SkuSellView view = service.getSellView(200L);

        assertEquals(200L, view.getSkuId());
        assertEquals(100L, view.getSpuId());
        assertEquals("华为 Mate 60 Pro", view.getSkuName());
        assertEquals(new BigDecimal("5999.00"), view.getPrice());
        assertEquals(1, view.getSkuStatus());
        assertEquals(0, view.getSpuStatus());
    }

    @Test
    void createAndView() {
        when(skuMapper.selectBySkuCode("SKU001")).thenReturn(null);
        when(skuMapper.insert(any(Sku.class))).thenAnswer(inv -> {
            Sku s = inv.getArgument(0);
            s.setId(300L);
            return 1;
        });

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
}
