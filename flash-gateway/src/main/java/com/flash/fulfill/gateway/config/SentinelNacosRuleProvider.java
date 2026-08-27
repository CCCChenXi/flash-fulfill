package com.flash.fulfill.gateway.config;

import com.alibaba.csp.sentinel.adapter.gateway.common.rule.GatewayFlowRule;
import com.alibaba.csp.sentinel.adapter.gateway.common.rule.GatewayRuleManager;
import com.alibaba.csp.sentinel.datasource.nacos.NacosDataSource;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.util.Set;

/**
 * Sentinel 网关流控规则 Nacos 数据源。
 * <p>
 * 启动顺序(@DependsOn 保证基线先于注册):{@link SentinelGatewayConfig} 先加载代码兜底基线规则,
 * 本组件再注册 Nacos 数据源;Nacos 有配置后异步覆盖基线并支持热更新,Nacos 不可用/未配置时回落到基线。
 * <p>
 * 说明:仅流控规则(GatewayFlowRule)走 Nacos 下发;API 定义保持代码内置(见 {@link GatewayRuleConverters})。
 * 规则热更新仅在网关本地生效(Sentinel 每实例本地计数),多实例全局精确限流需另行叠加 Redis/集群模式。
 */
@Slf4j
@Component
@DependsOn("sentinelGatewayConfig")
@ConditionalOnProperty(name = "gateway.sentinel-rules.nacos.enabled", havingValue = "true", matchIfMissing = true)
public class SentinelNacosRuleProvider {

    private final String serverAddr;
    private final String groupId;
    private final String flowDataId;
    private final ObjectMapper objectMapper;
    private final GatewayRuleDefaults ruleDefaults;

    public SentinelNacosRuleProvider(
            @Value("${gateway.sentinel-rules.nacos.server-addr:172.25.80.175:8848}") String serverAddr,
            @Value("${gateway.sentinel-rules.nacos.group-id:SENTINEL_GROUP}") String groupId,
            @Value("${gateway.sentinel-rules.nacos.flow-data-id:flash-gateway-flow-rules}") String flowDataId,
            ObjectMapper objectMapper,
            GatewayRuleDefaults ruleDefaults) {
        this.serverAddr = serverAddr;
        this.groupId = groupId;
        this.flowDataId = flowDataId;
        this.objectMapper = objectMapper;
        this.ruleDefaults = ruleDefaults;
    }

    @PostConstruct
    public void init() {
        if (serverAddr == null || serverAddr.isBlank()) {
            log.warn("gateway.sentinel-rules.nacos.server-addr 未配置,仅使用代码兜底规则");
            return;
        }
        NacosDataSource<Set<GatewayFlowRule>> flowDs = new NacosDataSource<>(serverAddr, groupId, flowDataId,
                GatewayRuleConverters.flowRules(objectMapper, ruleDefaults));

        GatewayRuleManager.register2Property(flowDs.getProperty());
        log.info("Sentinel 网关限流规则已挂接 Nacos:server={} group={} flowDataId={}",
                serverAddr, groupId, flowDataId);
    }
}