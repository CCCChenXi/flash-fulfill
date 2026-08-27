package com.flash.fulfill.fulfillment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.flash.fulfill.fulfillment.entity.DispatchRecord;
import org.apache.ibatis.annotations.Param;

/**
 * 派单记录 Mapper:新增走 BaseMapper,按订单号查询/存在性判断走 XML 自定义 SQL。
 */
public interface DispatchRecordMapper extends BaseMapper<DispatchRecord> {

    DispatchRecord selectByOrderNo(@Param("orderNo") String orderNo);

    /** 幂等判断:同一订单是否已派过单 */
    boolean existsByOrderNo(@Param("orderNo") String orderNo);
}
