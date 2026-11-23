package com.javmotion.api.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** {@code javmotion.worker.*} — how the API reaches the private execution worker. */
@ConfigurationProperties(prefix = "javmotion.worker")
public record WorkerClientProperties(String baseUrl, String token, Duration timeout) {}
