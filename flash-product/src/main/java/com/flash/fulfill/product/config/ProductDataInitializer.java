package com.flash.fulfill.product.config;

import com.flash.fulfill.product.entity.Sku;
import com.flash.fulfill.product.entity.Spu;
import com.flash.fulfill.product.mapper.SkuMapper;
import com.flash.fulfill.product.mapper.SpuMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * 演示数据预热:初始化 category / brand / spu / sku。
 * <p>
 * TODO 生产:数据由商品/采购中心维护,此处仅演示。category / brand 暂无 Java 实体,直接用 JdbcTemplate。
 */
@Slf4j
@Component
public class ProductDataInitializer implements ApplicationRunner {

    private final SpuMapper spuMapper;
    private final SkuMapper skuMapper;
    private final JdbcTemplate jdbcTemplate;

    @Value("${product.seed.enabled:true}")
    private boolean seedEnabled;

    public ProductDataInitializer(SpuMapper spuMapper, SkuMapper skuMapper, JdbcTemplate jdbcTemplate) {
        this.spuMapper = spuMapper;
        this.skuMapper = skuMapper;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!seedEnabled) {
            return;
        }
        seedCategory();
        seedBrand();
        seedSpu(1L, "限量款智能手机");
        seedSpu(2L, "联名限量跑鞋");
        seedSku(1001L, 1L, "SKU1001", "限量款智能手机 · 标准版", new BigDecimal("99.00"));
        seedSku(1002L, 2L, "SKU1002", "联名限量跑鞋 · 42码", new BigDecimal("199.00"));
    }

    private void seedCategory() {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM category WHERE id = 1", Integer.class);
        if (count == null || count == 0) {
            jdbcTemplate.update(
                    "INSERT INTO category (id, parent_id, name, sort, status) VALUES (1, 0, '默认分类', 0, 1)");
            log.info("已初始化演示分类 categoryId=1 name=默认分类");
        }
    }

    private void seedBrand() {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM brand WHERE id = 1", Integer.class);
        if (count == null || count == 0) {
            jdbcTemplate.update(
                    "INSERT INTO brand (id, name, status) VALUES (1, 'Flash品牌', 1)");
            log.info("已初始化演示品牌 brandId=1 name=Flash品牌");
        }
    }

    private void seedSpu(Long id, String name) {
        if (spuMapper.selectById(id) == null) {
            Spu spu = new Spu();
            spu.setId(id);
            spu.setName(name);
            spu.setCategoryId(1L);
            spu.setBrandId(1L);
            spu.setStatus(1);
            spuMapper.insert(spu);
            log.info("已初始化演示 SPU id={} name={}", id, name);
        }
    }

    private void seedSku(Long id, Long spuId, String skuCode, String name, BigDecimal price) {
        if (skuMapper.selectBySkuCode(skuCode) == null) {
            Sku sku = new Sku();
            sku.setId(id);
            sku.setSpuId(spuId);
            sku.setSkuCode(skuCode);
            sku.setName(name);
            sku.setPrice(price);
            sku.setStatus(1);
            skuMapper.insert(sku);
            log.info("已初始化演示 SKU id={} skuCode={} name={}", id, skuCode, name);
        }
    }
}
