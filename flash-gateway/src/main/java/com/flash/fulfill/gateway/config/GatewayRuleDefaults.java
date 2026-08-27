package com.flash.fulfill.gateway.config;

import com.alibaba.csp.sentinel.adapter.gateway.common.SentinelGatewayConstants;
import com.alibaba.csp.sentinel.adapter.gateway.common.api.ApiDefinition;
import com.alibaba.csp.sentinel.adapter.gateway.common.api.ApiPathPredicateItem;
import com.alibaba.csp.sentinel.adapter.gateway.common.rule.GatewayFlowRule;
import com.alibaba.csp.sentinel.adapter.gateway.common.rule.GatewayParamFlowItem;
import com.flash.fulfill.gateway.filter.ClientIdentityFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Set;

/**
 * 网关限流默认规则构建器。
 * <p>
 * 由 {@code gateway.rate-limit.*} 配置构造兜底规则:作为启动基线立即加载,
 * 同时作为 Nacos 数据源未配置数据时的回退(SentinelNacosRuleProvider 使用)。
 */
@Component
public class GatewayRuleDefaults {

    public static final String FLASH_API = "flash-api";

    private final long apiQps;
    private final long ipQps;
    private final long userQps;

    public GatewayRuleDefaults(
            @Value("${gateway.rate-limit.api-qps:5000}") long apiQps,
            @Value("${gateway.rate-limit.ip-qps:20}") long ipQps,
            @Value("${gateway.rate-limit.user-qps:100}") long userQps) {
        this.apiQps = apiQps;
        this.ipQps = ipQps;
        this.userQps = userQps;
    }

    /** 默认流控规则:全局 / 按可信 IP / 按登录用户 */
    public Set<GatewayFlowRule> flowRules() {
        Set<GatewayFlowRule> rules = new HashSet<>();
        rules.add(new GatewayFlowRule(FLASH_API)
                .setResourceMode(SentinelGatewayConstants.RESOURCE_MODE_CUSTOM_API_NAME)
                .setCount(apiQps));

        rules.add(new GatewayFlowRule(FLASH_API)
                .setResourceMode(SentinelGatewayConstants.RESOURCE_MODE_CUSTOM_API_NAME)
                .setCount(ipQps)
                .setParamItem(new GatewayParamFlowItem()
                        .setParseStrategy(SentinelGatewayConstants.PARAM_PARSE_STRATEGY_HEADER)
                        .setFieldName(ClientIdentityFilter.CLIENT_IP_HEADER)));

        rules.add(new GatewayFlowRule(FLASH_API)
                .setResourceMode(SentinelGatewayConstants.RESOURCE_MODE_CUSTOM_API_NAME)
                .setCount(userQps)
                .setParamItem(new GatewayParamFlowItem()
                        .setParseStrategy(SentinelGatewayConstants.PARAM_PARSE_STRATEGY_HEADER)
                        .setFieldName("X-User-Id")));
        return rules;
    }

    /** 默认 API 定义:匹配全部路径 */
    public Set<ApiDefinition> apiDefinitions() {
        Set<ApiDefinition> defs = new HashSet<>();
        Set<com.alibaba.csp.sentinel.adapter.gateway.common.api.ApiPredicateItem> predicates = new HashSet<>();
        predicates.add(new ApiPathPredicateItem().setPattern("/**"));
        defs.add(new ApiDefinition(FLASH_API).setPredicateItems(predicates));
        return defs;
    }
}