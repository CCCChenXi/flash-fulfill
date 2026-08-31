package com.flash.fulfill.fulfillment.feign;

import com.flash.fulfill.common.api.Result;
import com.flash.fulfill.common.constant.ApiPaths;
import com.flash.fulfill.common.constant.ServiceNames;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;

/**
 * 订单中心回调客户端(内网调用)。
 */
@FeignClient(name = ServiceNames.ORDER, contextId = "orderInternalClient")
public interface OrderClient {

    /**
     * 通知订单服务该订单已完成发货。
     */
    @PutMapping(ApiPaths.ORDER_INTERNAL_DISPATCH)
    Result<Boolean> markDispatched(@PathVariable("orderNo") String orderNo);
}