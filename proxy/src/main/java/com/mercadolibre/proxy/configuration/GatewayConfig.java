package com.mercadolibre.proxy.configuration;

import com.mercadolibre.proxy.filter.CustomRateLimiterFilter;
import com.mercadolibre.proxy.model.Request;
import com.mercadolibre.proxy.repository.RequestRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import reactor.core.publisher.Mono;

import java.util.Date;
import java.util.concurrent.ConcurrentHashMap;

@Configuration
@Slf4j
@RequiredArgsConstructor
@EnableConfigurationProperties(RateLimiterProperties.class)
public class GatewayConfig {

    private final ConcurrentHashMap<String, String> requestCache = new ConcurrentHashMap<>();
    private final RateLimiterProperties properties;

    @Bean
    public RouteLocator customRouteLocator(RouteLocatorBuilder builder, CustomRateLimiterFilter customRateLimiterFilter) {
        return builder.routes()
                .route("proxy_to_ml", r -> r
                        .path("/api/**")
                        .filters(f -> f.filter(customRateLimiterFilter))
                        .uri("https://api.mercadolibre.com"))
                .build();
    }

    @Primary
    @Bean
    public KeyResolver customKeyResolver(RequestRepository requestRepository) {
        return exchange -> {
            String ip = exchange.getRequest().getRemoteAddress().getAddress().getHostAddress();
            String path = exchange.getRequest().getPath().value();
            String key;
            String limiterType;

            if (requestCache.containsKey(ip)) {
                key = ip;
                limiterType = "IP";
                log.info("Using IP Rate Limiter for ip: {}", ip);
            } else if (requestCache.containsValue(path)) {
                key = path;
                limiterType = "Path";
                log.info("Using Path Rate Limiter for path: {}", path);
            } else {
                key = ip + ":" + path;
                limiterType = "IP+Path";
                requestCache.put(ip, path);
                log.info("Using IP + Path Rate Limiter for ip and path: {}", key);
            }

            exchange.getAttributes().put("rate-limiter-type", limiterType);

            //Save the request in Redis
            Request request = new Request();
            request.setIp(ip);
            request.setPath(path);
            request.setMethod(exchange.getRequest().getMethod().toString());
            request.setDate(new Date());
            long startTime = System.currentTimeMillis();

            //Callback before of the response and update status and duration
            exchange.getResponse().beforeCommit(() -> {
                long duration = System.currentTimeMillis() - startTime;
                request.setDuration(duration);
                request.setStatus(exchange.getResponse().getStatusCode().value());
                Request savedRequest = requestRepository.save(request);
                log.info("Request completed - Duration: {}ms, Status: {}, Path: {}",
                        duration,
                        savedRequest.getStatus(),
                        savedRequest.getPath());
                return Mono.empty();
            });

            return Mono.just(key);
        };
    }

    @Primary
    @Bean
    public RedisRateLimiter defaultRedisRateLimiter() {
        return new RedisRateLimiter(1, 3, 1);
    }

    @Bean
    @Qualifier("ipRateLimiter")
    public RedisRateLimiter ipRateLimiter() {
        return new RedisRateLimiter(
            properties.getIp().getReplenishRate(),
            properties.getIp().getBurstCapacity(),
            properties.getIp().getRequestedTokens()
        );
    }

    @Bean
    @Qualifier("pathRateLimiter")
    public RedisRateLimiter pathRateLimiter() {
        return new RedisRateLimiter(
            properties.getPath().getReplenishRate(),
            properties.getPath().getBurstCapacity(),
            properties.getPath().getRequestedTokens()
        );
    }

    @Bean
    @Qualifier("ipAndPathRateLimiter")
    public RedisRateLimiter ipAndPathRateLimiter() {
        return new RedisRateLimiter(
            properties.getIpPath().getReplenishRate(),
            properties.getIpPath().getBurstCapacity(),
            properties.getIpPath().getRequestedTokens()
        );
    }

    @Bean
    public KeyResolver ipKeyResolver() {
        return exchange -> {
            String ip = exchange.getRequest().getRemoteAddress().getAddress().getHostAddress();
            return Mono.just(ip);
        };
    }

    public KeyResolver pathKeyResolver() {
        return exchange -> Mono.just(
                exchange.getRequest().getPath().value()
        );
    }

    public KeyResolver ipAndPathKeyResolver() {
        return exchange -> {
            String ip = exchange.getRequest().getRemoteAddress().getAddress().getHostAddress();
            String path = exchange.getRequest().getURI().getPath();
            String key = ip + ":" + path;
            return Mono.just(key);
        };
    }
}
