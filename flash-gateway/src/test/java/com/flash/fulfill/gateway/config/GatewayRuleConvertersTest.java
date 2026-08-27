package com.flash.fulfill.gateway.config;

import com.alibaba.csp.sentinel.adapter.gateway.common.SentinelGatewayConstants;
import com.alibaba.csp.sentinel.adapter.gateway.common.rule.GatewayFlowRule;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flash.fulfill.gateway.filter.ClientIdentityFilter;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GatewayRuleConvertersTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final GatewayRuleDefaults defaults = new GatewayRuleDefaults(5000, 20, 100);

    @Test
    void nullOrBlankFlowRulesFallsBackToDefaults() {
        // GatewayParamFlowItem 无 equals,不能整体比较 Set,改为逐规则校验
        assertMatchesDefaults(GatewayRuleConverters.flowRules(objectMapper, defaults).convert(null));
        assertMatchesDefaults(GatewayRuleConverters.flowRules(objectMapper, defaults).convert("  "));
    }

    @Test
    void emptyArrayClearsFlowRules() {
        Set<GatewayFlowRule> rules = GatewayRuleConverters.flowRules(objectMapper, defaults).convert("[]");

        assertTrue(rules.isEmpty());
    }

    @Test
    void validFlowRulesJsonIsParsed() {
        String json = "[{\"resource\":\"flash-api\",\"resourceMode\":1,\"count\":42,"
                + "\"paramItem\":{\"parseStrategy\":2,\"fieldName\":\"X-User-Id\"}}]";

        Set<GatewayFlowRule> rules = GatewayRuleConverters.flowRules(objectMapper, defaults).convert(json);

        assertEquals(1, rules.size());
        GatewayFlowRule rule = rules.iterator().next();
        assertEquals("flash-api", rule.getResource());
        assertEquals(42, rule.getCount(), 0.001);
        assertEquals("X-User-Id", rule.getParamItem().getFieldName());
    }

    @Test
    void malformedFlowRulesJsonFallsBackToDefaults() {
        assertMatchesDefaults(GatewayRuleConverters.flowRules(objectMapper, defaults).convert("{not-json"));
    }

    private void assertMatchesDefaults(Set<GatewayFlowRule> rules) {
        assertEquals(3, rules.size());
        assertTrue(rules.stream().anyMatch(r -> r.getCount() == 5000 && r.getParamItem() == null));
        assertTrue(rules.stream()
                .filter(r -> r.getCount() == 20)
                .anyMatch(r -> r.getParamItem() != null
                        && r.getParamItem().getParseStrategy() == SentinelGatewayConstants.PARAM_PARSE_STRATEGY_HEADER
                        && ClientIdentityFilter.CLIENT_IP_HEADER.equals(r.getParamItem().getFieldName())));
        assertTrue(rules.stream()
                .filter(r -> r.getCount() == 100)
                .anyMatch(r -> r.getParamItem() != null
                        && r.getParamItem().getParseStrategy() == SentinelGatewayConstants.PARAM_PARSE_STRATEGY_HEADER
                        && "X-User-Id".equals(r.getParamItem().getFieldName())));
        assertFalse(rules.stream().anyMatch(r -> r.getParamItem() != null && r.getParamItem().getIndex() != null));
    }
}