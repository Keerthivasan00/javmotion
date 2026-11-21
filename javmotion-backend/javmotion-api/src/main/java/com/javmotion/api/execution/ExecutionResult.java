package com.javmotion.api.execution;

import com.javmotion.common.job.ExecutionStatus;

/**
 * Outcome of a finished run as reported by the worker (Step B4).
 *
 * @param compileErrorsJson JSON array of {@code {line, column, message}}, or null
 */
public record ExecutionResult(
        ExecutionStatus status,
        String stdout,
        String stderr,
        String compileErrorsJson,
        Integer exitCode,
        Integer executionTimeMs,
        String errorMessage) {}
