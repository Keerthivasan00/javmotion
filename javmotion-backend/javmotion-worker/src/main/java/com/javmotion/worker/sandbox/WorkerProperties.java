package com.javmotion.worker.sandbox;

import java.nio.file.Path;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Typed {@code worker.*} settings (application.yml, overridable by env vars). */
@ConfigurationProperties(prefix = "worker")
public record WorkerProperties(
        String token, String sandboxImage, int maxConcurrent, Path jobsDir, Limits limits) {

    public record Limits(
            String memory,
            String cpus,
            int pids,
            String tmpfsSize,
            Duration runTimeout,
            Duration visualizeTimeout,
            int maxOutputBytes) {}
}
