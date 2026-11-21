package com.javmotion.api.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Typed {@code javmotion.*} settings from application.yml. */
@ConfigurationProperties(prefix = "javmotion")
public record AppProperties(String version, Cors cors) {

    public record Cors(List<String> allowedOrigins) {}
}
