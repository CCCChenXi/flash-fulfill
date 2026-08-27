package com.flash.fulfill.product.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.flash.fulfill.product.entity.Spu;
import org.apache.ibatis.annotations.Param;

/**
 * SPU Mapper:新增 / 按 ID 查询 / 更新走 BaseMapper,按 ID 查视图走 XML 自定义 SQL。
 */
public interface SpuMapper extends BaseMapper<Spu> {

    Spu selectById4View(@Param("id") Long id);
}
