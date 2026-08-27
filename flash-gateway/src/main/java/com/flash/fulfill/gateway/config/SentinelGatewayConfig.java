package com.flash.fulfill.gateway.config;

import com.alibaba.csp.sentinel.adapter.gateway.common.api.GatewayApiDefinitionManager;
import com.alibaba.csp.sentinel.adapter.gateway.common.rule.GatewayRuleManager;
import com.alibaba.csp.sentinel.adapter.gateway.sc.SentinelGatewayFilter;
import com.alibaba.csp.sentinel.adapter.gateway.sc.callback.GatewayCallbackManager;
import com.alibaba.csp.sentinel.adapter.gateway.sc.exception.SentinelGatewayBlockExceptionHandler;
import com.flash.fulfill.common.api.ErrorCode;
import com.flash.fulfill.common.api.Result;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerCodecConfigurer;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.reactive.result.view.ViewResolver;

import java.util.List;

/**
 * Sentinel 网关限流配置。
 * <p>
 * 规则来源:
 * <ul>
 *     <li>启动基线: {@link GatewayRuleDefaults}(由 {@code gateway.rate-limit.*} 构造)</li>
 *     <li>运行时覆盖: {@link SentinelNacosRuleProvider} 从 Nacos 下发并热更新</li>
 * </ul>
 * 被限流时抛出 BlockException,由 SentinelGatewayBlockExceptionHandler 交给自定义 block handler
 * 统一返回 429 + Retry-After。
 * <p>
 * 注意:Sentinel 默认按实例本地计数,多实例部署不共享全局额度;如需全局精确限流需叠加 Redis 或集群模式。
 */
@Slf4j
@Configuration
public class SentinelGatewayConfig {

    private final GatewayRuleDefaults ruleDefaults;

    public SentinelGatewayConfig(GatewayRuleDefaults ruleDefaults) {
        this.ruleDefaults = ruleDefaults;
    }

    /** 拦截 BlockException 的 WebExceptionHandler,须先于 Spring 默认错误处理执行 */
    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    public SentinelGatewayBlockExceptionHandler sentinelGatewayBlockExceptionHandler(
            List<ViewResolver> viewResolvers, ServerCodecConfigurer serverCodecConfigurer) {
        return new SentinelGatewayBlockExceptionHandler(viewResolvers, serverCodecConfigurer);
    }

    /**
     * Sentinel 网关限流过滤器。
     * 顺序须在 ClientIdentityFilter(-200) 与 JwtAuthFilter(-100) 之后,确保 IP/用户头已就绪;
     * 默认构造器为 Integer.MIN_VALUE(最先执行),故显式指定 order=-1。
     */
    @Bean
    public GlobalFilter sentinelGatewayFilter() {
        return new SentinelGatewayFilter(-1);
    }

    @PostConstruct
    public void init() {
        GatewayCallbackManager.setBlockHandler((exchange, ex) ->
                ServerResponse.status(HttpStatus.TOO_MANY_REQUESTS)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Retry-After", "1")
                        .bodyValue(Result.fail(ErrorCode.TOO_MANY_REQUESTS)));

        // 启动基线:Nacos 未下发前网关也有防护;Nacos 数据源就绪后覆盖
        GatewayRuleManager.loadRules(ruleDefaults.flowRules());
        GatewayApiDefinitionManager.loadApiDefinitions(ruleDefaults.apiDefinitions());
        log.info("Sentinel 网关限流基线规则已加载");
    }
}