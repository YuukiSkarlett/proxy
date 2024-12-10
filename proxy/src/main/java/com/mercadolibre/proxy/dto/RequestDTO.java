package com.mercadolibre.proxy.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Builder
@AllArgsConstructor
@NoArgsConstructor
@Data
public class RequestDTO {
    private long totalRequests;
    private long successfulRequests;
    private long failedRequests;
    private double averageDuration;
    private long rateLimitedRequests;
}