package com.javmotion.common.job;

/**
 * One javac diagnostic, positioned for the editor (1-based line and column).
 * Stored as JSON in {@code executions.compile_errors} and sent to the frontend as Monaco markers.
 */
public record CompileError(int line, int column, String message) {}
