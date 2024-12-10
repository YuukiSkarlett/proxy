package com.mercadolibre.proxy.configuration;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "spring.cloud.gateway.rate-limiter")
@Data
public class RateLimiterProperties {
    private LimiterConfig ip;
    private LimiterConfig path;
    private LimiterConfig ipPath;

    @Data
    public static class LimiterConfig {
        private int replenishRate;
        private int burstCapacity;
        private int requestedTokens;
    }
} 