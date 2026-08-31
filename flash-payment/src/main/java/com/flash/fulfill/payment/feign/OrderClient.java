package com.flash.fulfill.payment.feign;

import com.flash.fulfill.common.api.Result;
import com.flash.fulfill.common.constant.ApiPaths;
import com.flash.fulfill.common.constant.ServiceNames;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;

/**
 * 订单中心回调客户端(内网调用,支付成功通知订单置已支付)。
 */
@FeignClient(name = ServiceNames.ORDER, contextId = "paymentOrderClient")
public interface OrderClient {

    @PutMapping(ApiPaths.ORDER_INTERNAL_PAID)
    Result<Boolean> markPaid(@PathVariable("orderNo") String orderNo);
}