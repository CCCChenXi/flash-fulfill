package com.flash.fulfill.product.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 新建 SKU 请求。
 */
@Data
@NoArgsConstructor
public class SkuCreateCommand {

    @NotNull(message = "SPU ID 不能为空")
    private Long spuId;

    @NotBlank(message = "SKU 编码不能为空")
    private String skuCode;

    @NotBlank(message = "SKU 名称不能为空")
    private String name;

    @NotNull(message = "价格不能为空")
    @DecimalMin(value = "0.00", message = "价格不能小于 0")
    private BigDecimal price;

    private String image;

    private String specs;
}
