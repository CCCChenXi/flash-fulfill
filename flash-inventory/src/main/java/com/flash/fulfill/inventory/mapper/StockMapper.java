package com.flash.fulfill.inventory.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.flash.fulfill.inventory.entity.Stock;
import org.apache.ibatis.annotations.Param;

/**
 * 库存 Mapper:标准 CRUD 走 BaseMapper,扣减/按 SKU 查询走 XML 自定义 SQL。
 */
public interface StockMapper extends BaseMapper<Stock> {

    /**
     * 乐观锁条件扣减:仅当可用库存充足时扣减,返回受影响行数(0 表示库存不足)。
     * 单条 UPDATE 天然原子,避免超卖。
     */
    int deductStock(@Param("skuId") Long skuId, @Param("qty") int qty);

    Stock selectBySkuId(@Param("skuId") Long skuId);
}
