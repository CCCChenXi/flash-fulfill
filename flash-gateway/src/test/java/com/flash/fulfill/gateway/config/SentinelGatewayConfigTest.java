package com.flash.fulfill.gateway.config;

import com.alibaba.csp.sentinel.adapter.gateway.common.SentinelGatewayConstants;
import com.alibaba.csp.sentinel.adapter.gateway.common.api.GatewayApiDefinitionManager;
import com.alibaba.csp.sentinel.adapter.gateway.common.rule.GatewayFlowRule;
import com.alibaba.csp.sentinel.adapter.gateway.common.rule.GatewayRuleManager;
import com.alibaba.csp.sentinel.adapter.gateway.sc.callback.GatewayCallbackManager;
import com.alibaba.csp.sentinel.slots.block.flow.FlowException;
import com.flash.fulfill.gateway.filter.ClientIdentityFilter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.reactive.function.server.ServerResponse;

import java.time.Duration;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SentinelGatewayConfigTest {

    private SentinelGatewayConfig config;

    @BeforeEach
    void setUp() {
        config = new SentinelGatewayConfig(new GatewayRuleDefaults(5000, 20, 100));
        config.init();
    }

    @Test
    void loadsThreeRulesOnFlashApi() {
        Set<GatewayFlowRule> rules = GatewayRuleManager.getRulesForResource("flash-api");

        assertEquals(3, rules.size());
        assertTrue(rules.stream().anyMatch(r -> r.getCount() == 5000 && r.getParamItem() == null));
    }

    @Test
    void ipRuleReadsClientIpHeader() {
        Optional<GatewayFlowRule> ipRule = GatewayRuleManager.getRulesForResource("flash-api").stream()
                .filter(r -> r.getCount() == 20)
                .findFirst();

        assertTrue(ipRule.isPresent());
        assertEquals(SentinelGatewayConstants.PARAM_PARSE_STRATEGY_HEADER, ipRule.get().getParamItem().getParseStrategy());
        assertEquals(ClientIdentityFilter.CLIENT_IP_HEADER, ipRule.get().getParamItem().getFieldName());
    }

    @Test
    void userRuleReadsUserIdHeader() {
        Optional<GatewayFlowRule> userRule = GatewayRuleManager.getRulesForResource("flash-api").stream()
                .filter(r -> r.getCount() == 100)
                .findFirst();

        assertTrue(userRule.isPresent());
        assertEquals(SentinelGatewayConstants.PARAM_PARSE_STRATEGY_HEADER, userRule.get().getParamItem().getParseStrategy());
        assertEquals("X-User-Id", userRule.get().getParamItem().getFieldName());
    }

    @Test
    void registersFlashApiDefinition() {
        assertTrue(GatewayApiDefinitionManager.getApiDefinitions().stream()
                .anyMatch(api -> api.getApiName().equals("flash-api")));
    }

    @Test
    void blockHandlerReturns429WithRetryAfter() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/seckill/flash-orders").build());

        ServerResponse response = GatewayCallbackManager.getBlockHandler()
                .handleRequest(exchange, new FlowException("blocked"))
                .block(Duration.ofSeconds(1));

        assertEquals(HttpStatus.TOO_MANY_REQUESTS, response.statusCode());
        assertEquals("1", response.headers().getFirst("Retry-After"));
        assertEquals(MediaType.APPLICATION_JSON.toString(), response.headers().getFirst("Content-Type"));
    }
}