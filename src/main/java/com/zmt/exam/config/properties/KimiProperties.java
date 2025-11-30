package com.zmt.exam.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "kimi.api")
@Data
public class KimiProperties {

    private String model;
    private String uri;
    private String apiKey;
    private Integer maxTokens;
    private Double temperature;
}