package com.javmotion.tracer;

/**
 * Hard caps from backend doc §6.4. Hitting steps, bytes or time stops tracing and marks the
 * trace {@code truncated}; the object / element / string caps shrink what one step shows.
 */
public record TraceLimits(
        int maxSteps,
        long maxBytes,
        long maxMillis,
        int maxObjectsPerStep,
        int maxArrayElements,
        int maxStringChars,
        int maxHeapDepth,
        int maxOutputBytes) {

    public static TraceLimits defaults() {
        return new TraceLimits(5_000, 10L * 1024 * 1024, 8_000, 200, 100, 1_000, 4, 64 * 1024);
    }

    public TraceLimits withMaxMillis(long millis) {
        return new TraceLimits(maxSteps, maxBytes, millis, maxObjectsPerStep, maxArrayElements, maxStringChars,
                maxHeapDepth, maxOutputBytes);
    }

    public TraceLimits withMaxSteps(int steps) {
        return new TraceLimits(steps, maxBytes, maxMillis, maxObjectsPerStep, maxArrayElements, maxStringChars,
                maxHeapDepth, maxOutputBytes);
    }
}
