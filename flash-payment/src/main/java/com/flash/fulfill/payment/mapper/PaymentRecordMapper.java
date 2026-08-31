package com.flash.fulfill.payment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.flash.fulfill.payment.entity.PaymentRecord;
import org.apache.ibatis.annotations.Param;

/**
 * 支付单 Mapper:新增/更新走 BaseMapper,按支付单号/订单号查询走 XML 自定义 SQL。
 */
public interface PaymentRecordMapper extends BaseMapper<PaymentRecord> {

    PaymentRecord selectByPayNo(@Param("payNo") String payNo);

    PaymentRecord selectByOrderNo(@Param("orderNo") String orderNo);
}