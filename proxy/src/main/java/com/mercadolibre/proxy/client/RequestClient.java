package com.mercadolibre.proxy.client;


import com.mercadolibre.proxy.configuration.FeignConfig;
import com.mercadolibre.proxy.dto.RequestDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "statistics-service", url = "${statistics.service.url}", configuration = FeignConfig.class)
public interface RequestClient {

    @PostMapping("/api/stats/requests")
    void sendStats(@RequestBody RequestDTO stats);
}