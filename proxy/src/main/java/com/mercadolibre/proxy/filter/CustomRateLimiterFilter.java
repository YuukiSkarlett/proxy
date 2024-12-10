package com.mercadolibre.proxy.filter;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
@Slf4j
public class CustomRateLimiterFilter implements GatewayFilter {

    private final KeyResolver customKeyResolver;
    private final RedisRateLimiter ipRateLimiter;
    private final RedisRateLimiter pathRateLimiter;
    private final RedisRateLimiter ipAndPathRateLimiter;

    public CustomRateLimiterFilter(
        KeyResolver customKeyResolver,
        @Qualifier("ipRateLimiter") RedisRateLimiter ipRateLimiter,
        @Qualifier("pathRateLimiter") RedisRateLimiter pathRateLimiter,
        @Qualifier("ipAndPathRateLimiter") RedisRateLimiter ipAndPathRateLimiter
    ) {
        this.customKeyResolver = customKeyResolver;
        this.ipRateLimiter = ipRateLimiter;
        this.pathRateLimiter = pathRateLimiter;
        this.ipAndPathRateLimiter = ipAndPathRateLimiter;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        return customKeyResolver.resolve(exchange)
            .flatMap(key -> {
                String limiterType = exchange.getAttribute("rate-limiter-type");
                RedisRateLimiter selectedLimiter;

                switch (limiterType) {
                    case "IP+Path":
                        selectedLimiter = ipAndPathRateLimiter;
                        break;
                    case "Path":
                        selectedLimiter = pathRateLimiter;
                        break;
                    case "IP":
                    default:
                        selectedLimiter = ipRateLimiter;
                }

                log.info("Using {} rate limiter for key: {}", limiterType, key);

                return selectedLimiter.isAllowed("rate-limit-key", key)
                    .flatMap(response -> {
                        response.getHeaders().forEach((headerName, headerValue) -> {
                            exchange.getResponse().getHeaders().add(headerName, headerValue.toString());
                        });
                        exchange.getResponse().getHeaders().add("X-RateLimit-Type", limiterType);
                        
                        if (response.isAllowed()) {
                            return chain.filter(exchange);
                        }
                        
                        log.info("Rate limit exceeded for {} limiter. Key: {}", limiterType, key);
                        exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
                        return exchange.getResponse().setComplete();
                    });
            });
    }
}