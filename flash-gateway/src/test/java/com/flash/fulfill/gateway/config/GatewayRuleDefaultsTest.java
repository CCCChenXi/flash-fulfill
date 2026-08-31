package com.flash.fulfill.gateway.config;

import com.alibaba.csp.sentinel.adapter.gateway.common.SentinelGatewayConstants;
import com.alibaba.csp.sentinel.adapter.gateway.common.api.ApiDefinition;
import com.alibaba.csp.sentinel.adapter.gateway.common.rule.GatewayFlowRule;
import com.flash.fulfill.gateway.constant.GatewayRuleConstants;
import com.flash.fulfill.gateway.filter.ClientIdentityFilter;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GatewayRuleDefaultsTest {

    private final GatewayRuleDefaults defaults = new GatewayRuleDefaults(5000, 20, 100);

    @Test
    void buildsThreeFlowRules() {
        Set<GatewayFlowRule> rules = defaults.flowRules();

        assertEquals(3, rules.size());
    }

    @Test
    void globalRuleHasNoParamItem() {
        assertTrue(defaults.flowRules().stream()
                .anyMatch(r -> r.getCount() == 5000 && r.getParamItem() == null));
    }

    @Test
    void ipRuleReadsClientIpHeader() {
        assertTrue(defaults.flowRules().stream()
                .filter(r -> r.getCount() == 20)
                .anyMatch(r -> r.getParamItem() != null
                        && r.getParamItem().getParseStrategy() == SentinelGatewayConstants.PARAM_PARSE_STRATEGY_HEADER
                        && ClientIdentityFilter.CLIENT_IP_HEADER.equals(r.getParamItem().getFieldName())));
    }

    @Test
    void userRuleReadsUserIdHeader() {
        assertTrue(defaults.flowRules().stream()
                .filter(r -> r.getCount() == 100)
                .anyMatch(r -> r.getParamItem() != null
                        && r.getParamItem().getParseStrategy() == SentinelGatewayConstants.PARAM_PARSE_STRATEGY_HEADER
                        && "X-User-Id".equals(r.getParamItem().getFieldName())));
    }

    @Test
    void apiDefinitionMatchesAllPaths() {
        Set<ApiDefinition> defs = defaults.apiDefinitions();

        assertTrue(defs.stream().anyMatch(api -> api.getApiName().equals(GatewayRuleConstants.FLASH_API)));
    }
}