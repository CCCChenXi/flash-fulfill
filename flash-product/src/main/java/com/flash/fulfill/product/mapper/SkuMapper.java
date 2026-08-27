package com.flash.fulfill.product.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.flash.fulfill.product.entity.Sku;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * SKU Mapper:新增 / 更新 / 按 ID 查询走 BaseMapper,查重与按 ID 查视图走 XML 自定义 SQL。
 */
public interface SkuMapper extends BaseMapper<Sku> {

    Sku selectBySkuCode(@Param("skuCode") String skuCode);

    Sku selectById4View(@Param("id") Long id);

    List<Long> selectIdsBySpuId(@Param("spuId") Long spuId);
}
