package com.javmotion.api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** {@code javmotion.execution.*} — input limits for code runs (backend doc §4.4). */
@ConfigurationProperties(prefix = "javmotion.execution")
public record ExecutionProperties(int maxSourceBytes, int maxStdinBytes) {}
