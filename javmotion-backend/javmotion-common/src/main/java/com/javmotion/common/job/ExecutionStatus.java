package com.javmotion.common.job;

/** Lifecycle states — backend doc §4.2. */
public enum ExecutionStatus {
    QUEUED,
    RUNNING,
    COMPLETED,
    COMPILE_ERROR,
    RUNTIME_ERROR,
    TIMEOUT,
    LIMIT_EXCEEDED,
    FAILED,
    CANCELLED;

    public boolean isTerminal() {
        return this != QUEUED && this != RUNNING;
    }
}
