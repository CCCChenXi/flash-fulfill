package com.flash.fulfill.fulfillment.controller;

import com.flash.fulfill.common.api.Result;
import com.flash.fulfill.common.constant.ApiPaths;
import com.flash.fulfill.fulfillment.entity.DispatchRecord;
import com.flash.fulfill.fulfillment.service.FulfillmentService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 履约查询接口。
 */
@RestController
public class FulfillmentController {

    private final FulfillmentService fulfillmentService;

    public FulfillmentController(FulfillmentService fulfillmentService) {
        this.fulfillmentService = fulfillmentService;
    }

    /**
     * 查询订单派单/履约记录。
     */
    @GetMapping(ApiPaths.FULFILLMENT_DISPATCH)
    public Result<DispatchRecord> queryDispatch(@PathVariable("orderNo") String orderNo) {
        return Result.ok(fulfillmentService.queryByOrderNo(orderNo));
    }
}