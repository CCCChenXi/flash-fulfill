CREATE TABLE IF NOT EXISTS category (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT 'CATEGORY ID',
    parent_id BIGINT NOT NULL DEFAULT 0 COMMENT 'parent category id: 0 means top-level',
    name VARCHAR(100) NOT NULL COMMENT 'category name',
    sort INT NOT NULL DEFAULT 0 COMMENT 'sort order',
    status TINYINT NOT NULL DEFAULT 1 COMMENT 'status: 0 disabled, 1 enabled',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_parent_id (parent_id),
    KEY idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='product category table';

CREATE TABLE IF NOT EXISTS brand (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT 'BRAND ID',
    name VARCHAR(100) NOT NULL COMMENT 'brand name',
    logo VARCHAR(500) DEFAULT NULL COMMENT 'brand logo',
    description VARCHAR(500) DEFAULT NULL COMMENT 'brand description',
    status TINYINT NOT NULL DEFAULT 1 COMMENT 'status: 0 disabled, 1 enabled',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_name (name),
    KEY idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='product brand table';

CREATE TABLE IF NOT EXISTS spu (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT 'SPU ID',
    name VARCHAR(200) NOT NULL COMMENT 'product name',
    category_id BIGINT NOT NULL COMMENT 'category id',
    brand_id BIGINT DEFAULT NULL COMMENT 'brand id',
    description TEXT COMMENT 'product description',
    main_image VARCHAR(500) DEFAULT NULL COMMENT 'main image',
    status TINYINT NOT NULL DEFAULT 0 COMMENT 'status: 0 off shelf, 1 on shelf',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'create time',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'update time',
    PRIMARY KEY (id),
    KEY idx_category_id (category_id),
    KEY idx_brand_id (brand_id),
    KEY idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='product SPU table';

CREATE TABLE IF NOT EXISTS sku (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT 'SKU ID',
    spu_id BIGINT NOT NULL COMMENT 'SPU ID',
    sku_code VARCHAR(64) NOT NULL COMMENT 'SKU code',
    name VARCHAR(200) NOT NULL COMMENT 'SKU name',
    price DECIMAL(12,2) NOT NULL COMMENT 'sale price',
    image VARCHAR(500) DEFAULT NULL COMMENT 'SKU image',
    specs JSON DEFAULT NULL COMMENT 'spec info',
    status TINYINT NOT NULL DEFAULT 0 COMMENT 'status: 0 off shelf, 1 on shelf',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'create time',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'update time',
    PRIMARY KEY (id),
    UNIQUE KEY uk_sku_code (sku_code),
    KEY idx_spu_id (spu_id),
    KEY idx_status (status),
    CONSTRAINT fk_sku_spu FOREIGN KEY (spu_id) REFERENCES spu(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='product SKU table';
