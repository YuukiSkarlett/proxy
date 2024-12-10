package com.mercadolibre.proxy.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.redis.core.RedisHash;
import java.util.Date;

@Builder
@Data
@AllArgsConstructor
@NoArgsConstructor
@RedisHash(value = "Request", timeToLive = 3600)
public class Request {
    @Id
    private String id;
    private String ip;
    private String path;
    private String method;
    private int status;
    private long duration;
    private Date date;
}
