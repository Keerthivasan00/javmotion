package com.javmotion.common.job;

import java.util.List;

/**
 * Worker → API: outcome of one job.
 *
 * @param status          terminal status (never QUEUED / RUNNING / CANCELLED)
 * @param compileErrors   parsed javac errors; empty unless COMPILE_ERROR
 * @param exitCode        program exit code, null if it never ran (compile error, worker failure)
 * @param outputTruncated stdout or stderr hit the output limit
 * @param errorMessage    human-readable reason for TIMEOUT / LIMIT_EXCEEDED / FAILED
 * @param traceJson       trace JSON for VISUALIZE (Step B5), else null
 */
public record JobResult(
        ExecutionStatus status,
        String stdout,
        String stderr,
        List<CompileError> compileErrors,
        Integer exitCode,
        long durationMs,
        boolean outputTruncated,
        String errorMessage,
        String traceJson) {

    public JobResult {
        compileErrors = compileErrors == null ? List.of() : List.copyOf(compileErrors);
    }

    public static JobResult failed(String message) {
        return new JobResult(ExecutionStatus.FAILED, "", "", List.of(), null, 0, false, message, null);
    }
}
