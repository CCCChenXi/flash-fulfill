package com.flash.fulfill.fulfillment.router;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 默认路由:按 skuId 取模选择仓库(演示用)。
 * <p>
 * TODO 生产实现详见 WarehouseRouter 接口注释。
 */
@Slf4j
@Component
public class DefaultWarehouseRouter implements WarehouseRouter {

    private static final String WAREHOUSE_SH = "WH-SH";
    private static final String WAREHOUSE_BJ = "WH-BJ";
    private static final String WAREHOUSE_GZ = "WH-GZ";

    private static final Map<Integer, String> WAREHOUSES = Map.of(
            0, WAREHOUSE_SH, 1, WAREHOUSE_BJ, 2, WAREHOUSE_GZ
    );

    @Override
    public String route(Long skuId, Long userId) {
        int bucket = Math.floorMod(skuId == null ? 0 : skuId, WAREHOUSES.size());
        return WAREHOUSES.get(bucket);
    }
}