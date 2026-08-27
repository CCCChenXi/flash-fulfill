package com.flash.fulfill.product.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 设置 SKU 状态请求(status:0 下架,1 上架)。
 */
@Data
@NoArgsConstructor
public class SkuStatusCommand {

    private int status;
}
