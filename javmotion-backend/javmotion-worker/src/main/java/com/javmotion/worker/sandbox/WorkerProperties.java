package com.javmotion.worker.sandbox;

import java.nio.file.Path;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Typed {@code worker.*} settings (application.yml, overridable by env vars).
 *
 * @param tracerJar javmotion-tracer.jar on the worker host — used by the host runner only; the
 *     sandbox image has its own copy at /opt/javmotion/tracer.jar
 */
@ConfigurationProperties(prefix = "worker")
public record WorkerProperties(
        String token, String sandboxImage, int maxConcurrent, Path jobsDir, Limits limits, Path tracerJar) {

    public record Limits(
            String memory,
            String cpus,
            int pids,
            String tmpfsSize,
            Duration runTimeout,
            Duration visualizeTimeout,
            int maxOutputBytes) {}
}
