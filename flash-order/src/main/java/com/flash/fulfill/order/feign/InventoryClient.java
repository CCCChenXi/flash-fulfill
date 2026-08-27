package com.flash.fulfill.order.feign;

import com.flash.fulfill.common.api.Result;
import com.flash.fulfill.common.dto.DeductStockCommand;
import com.flash.fulfill.common.dto.DeductStockResult;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * 库存服务回调客户端(内网调用)。
 */
@FeignClient(name = "flash-inventory", contextId = "inventoryClient", path = "/api/inventory")
public interface InventoryClient {

    /**
     * 扣减库存(原子条件更新,失败不抛错,以结果体判断成败)。
     */
    @PostMapping("/internal/deduct")
    Result<DeductStockResult> deduct(@RequestBody DeductStockCommand command);
}