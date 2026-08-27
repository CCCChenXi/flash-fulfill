package com.flash.fulfill.fulfillment.router;

/**
 * 智能仓配路由策略。
 * <p>
 * TODO 生产实现:综合「收货地址仓储距离 / 仓库可用库存 / 运费成本 / 运力饱和度」做最小成本派单,
 * 并结合库存预占与履约 SLA 实时协同调度。
 */
public interface WarehouseRouter {

    /**
     * 返回目标仓库编码。
     */
    String route(Long skuId, Long userId);
}