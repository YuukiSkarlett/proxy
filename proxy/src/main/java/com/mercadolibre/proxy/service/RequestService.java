package com.mercadolibre.proxy.service;

import com.mercadolibre.proxy.client.RequestClient;
import com.mercadolibre.proxy.dto.RequestDTO;
import com.mercadolibre.proxy.model.Request;
import com.mercadolibre.proxy.repository.RequestRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class RequestService {

    private final RequestRepository requestRepository;
    private final RequestClient requestClient;
    
    @Value("${app.features.stats-enabled:false}")
    private boolean statsEnabled;

    @Scheduled(fixedRate = 300000) //5 mins
    public void generateAndSendStats() {
        if (!statsEnabled) {
            log.info("Stats generation is disabled");
            return;
        }

        try {
            RequestDTO stats = generateStats();
            requestClient.sendStats(stats);
            log.info("Stats generated and sent");
        } catch (Exception e) {
            log.error("Error generating stats", e);
        }
    }

    private RequestDTO generateStats() {
        Date fiveMinutesAgo = new Date(System.currentTimeMillis() - 300000);

        List<Request> recentRequests = new ArrayList<>();
        for (Request request : requestRepository.findAll()) {
            if (request != null && request.getDate() != null && request.getDate().after(fiveMinutesAgo)) {
                recentRequests.add(request);
            }
        }

        return RequestDTO.builder()
                .totalRequests(recentRequests.size())
                .successfulRequests(recentRequests.stream()
                        .filter(req -> req.getStatus() >= 200 && req.getStatus() < 300)
                        .count())
                .failedRequests(recentRequests.stream()
                        .filter(req -> req.getStatus() >= 400 && req.getStatus() != 429)
                        .count())
                .rateLimitedRequests(recentRequests.stream()
                        .filter(req -> req.getStatus() == 429)
                        .count())
                .averageDuration(recentRequests.stream()
                        .mapToLong(Request::getDuration)
                        .average()
                        .orElse(0.0))
                .build();
    }
} 