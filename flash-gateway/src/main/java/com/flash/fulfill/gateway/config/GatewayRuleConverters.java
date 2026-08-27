package com.flash.fulfill.gateway.config;

import com.alibaba.csp.sentinel.adapter.gateway.common.rule.GatewayFlowRule;
import com.alibaba.csp.sentinel.datasource.Converter;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.util.Set;

/**
 * Nacos 配置 JSON → Sentinel 网关流控规则 的转换器。
 * <p>
 * 兜底语义:
 * <ul>
 *     <li>Nacos 数据为 null/空白 → 使用代码默认规则({@link GatewayRuleDefaults})</li>
 *     <li>Nacos 数据为 {@code []} → 清空规则(空集合)</li>
 *     <li>Nacos 数据为非法 JSON → 解析失败,回退代码默认规则并告警</li>
 *     <li>其余 → 解析覆盖默认</li>
 * </ul>
 * <p>
 * 注:API 定义保持代码内置({@code flash-api}=/**),不随 Nacos 下发——其类型为抽象
 * {@code ApiPredicateItem},无法直接反序列化,且作为全部路由的匹配骨架不宜动态化。
 */
@Slf4j
public final class GatewayRuleConverters {

    private GatewayRuleConverters() {
    }

    public static Converter<String, Set<GatewayFlowRule>> flowRules(
            ObjectMapper objectMapper, GatewayRuleDefaults defaults) {
        return json -> {
            if (json == null || json.isBlank()) {
                return defaults.flowRules();
            }
            try {
                return objectMapper.readValue(json, new TypeReference<Set<GatewayFlowRule>>() {
                });
            } catch (Exception e) {
                log.warn("网关流控规则 JSON 解析失败,回退默认规则 err={}", e.getMessage());
                return defaults.flowRules();
            }
        };
    }
}