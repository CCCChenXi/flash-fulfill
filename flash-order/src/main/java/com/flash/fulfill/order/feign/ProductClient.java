package com.flash.fulfill.order.feign;

import com.flash.fulfill.common.api.Result;
import com.flash.fulfill.common.dto.SkuSellView;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * 商品服务客户端(内网调用):下单计价读取出售视图。
 */
@FeignClient(name = "flash-product", contextId = "productClient", path = "/api/product")
public interface ProductClient {

    /**
     * 查询 SKU 出售视图,含真实单价与上下架状态。
     */
    @GetMapping("/sku/{skuId}")
    Result<SkuSellView> sellView(@PathVariable("skuId") Long skuId);
}
