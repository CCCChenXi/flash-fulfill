package com.flash.fulfill.fulfillment.service;

import com.flash.fulfill.common.api.ErrorCode;
import com.flash.fulfill.common.api.Result;
import com.flash.fulfill.common.dto.OrderFulfillEvent;
import com.flash.fulfill.common.exception.BizException;
import com.flash.fulfill.fulfillment.constant.FulfillmentConstant;
import com.flash.fulfill.fulfillment.entity.DispatchRecord;
import com.flash.fulfill.fulfillment.feign.OrderClient;
import com.flash.fulfill.fulfillment.mapper.DispatchRecordMapper;
import com.flash.fulfill.fulfillment.router.WarehouseRouter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 履约服务:消费订单事件 → 智能派单 → 回调订单置已发货。
 * <p>
 * TODO 生产增强:
 * 1. 派单与出库/物流轨迹解耦,轨迹事件经 Kafka/RocketMQ 广播;
 * 2. 失败重试 + 幂等(dispatch_record.order_no 唯一键);
 * 3. 接入 Seata AT 模式保障「派单记录 + 回调订单」的分布式一致性。
 */
@Slf4j
@Service
public class FulfillmentService {

    private final DispatchRecordMapper dispatchRecordMapper;
    private final WarehouseRouter warehouseRouter;
    private final OrderClient orderClient;

    public FulfillmentService(DispatchRecordMapper dispatchRecordMapper,
                              WarehouseRouter warehouseRouter,
                              OrderClient orderClient) {
        this.dispatchRecordMapper = dispatchRecordMapper;
        this.warehouseRouter = warehouseRouter;
        this.orderClient = orderClient;
    }

    @Transactional
    public void dispatch(OrderFulfillEvent event) {
        if (event == null || event.getOrderNo() == null) {
            log.warn("非法履约事件,忽略");
            return;
        }
        if (dispatchRecordMapper.existsByOrderNo(event.getOrderNo())) {
            log.info("重复履约事件已忽略 orderNo={}", event.getOrderNo());
            return;
        }

        DispatchRecord record = new DispatchRecord();
        record.setOrderNo(event.getOrderNo());
        record.setUserId(event.getUserId());
        record.setSkuId(event.getSkuId());
        record.setQuantity(event.getQuantity());
        record.setWarehouseCode(warehouseRouter.route(event.getSkuId(), event.getUserId()));
        record.setCarrierCode(FulfillmentConstant.CARRIER_CODE_DEFAULT);
        record.setTrackingNo(FulfillmentConstant.TRACKING_NO_PREFIX
                + event.getOrderNo().substring(Math.max(0,
                        event.getOrderNo().length() - FulfillmentConstant.TRACKING_NO_SUFFIX_LENGTH)));
        record.setStatus(FulfillmentConstant.STATUS_DISPATCHED);
        dispatchRecordMapper.insert(record);
        log.info("已派单 orderNo={} warehouse={} trackingNo={}",
                event.getOrderNo(), record.getWarehouseCode(), record.getTrackingNo());

        notifyOrderDispatched(event.getOrderNo());
    }

    private void notifyOrderDispatched(String orderNo) {
        try {
            Result<Boolean> r = orderClient.markDispatched(orderNo);
            log.info("通知订单置发货完成 orderNo={} result={}", orderNo, r);
        } catch (Exception e) {
            // TODO 生产:本地消息表重试,保证订单状态最终置为已发货
            log.error("通知订单置发货失败 orderNo={}", orderNo, e);
        }
    }

    public DispatchRecord queryByOrderNo(String orderNo) {
        DispatchRecord record = dispatchRecordMapper.selectByOrderNo(orderNo);
        if (record == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "未找到派单记录:" + orderNo);
        }
        return record;
    }
}
