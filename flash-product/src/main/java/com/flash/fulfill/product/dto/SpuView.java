package com.flash.fulfill.product.dto;

import lombok.Data;

/**
 * SPU 视图。
 */
@Data
public class SpuView {

    private Long id;

    private String name;

    private Long categoryId;

    private Long brandId;

    private String description;

    private String mainImage;

    private Integer status;
}
