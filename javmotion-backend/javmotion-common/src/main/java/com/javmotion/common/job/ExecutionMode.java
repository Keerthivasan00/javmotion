package com.javmotion.common.job;

public enum ExecutionMode {
    /** Compile + run, return output only. */
    RUN,
    /** Compile + run under the JDI tracer, return output + trace. */
    VISUALIZE
}
