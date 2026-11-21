package com.javmotion.common.trace;

import java.util.List;

/**
 * Full execution trace sent to the frontend.
 * Contract: docs/architecture/backend-architecture.md §6.
 * Frontend mirror: javmotion-frontend/src/lib/trace/types.ts — change both together.
 */
public record Trace(
        int schemaVersion,
        TraceSource source,
        String language,
        boolean truncated,
        List<TraceStep> steps) {

    public static final int SCHEMA_VERSION = 1;

    public static Trace concept(List<TraceStep> steps) {
        return new Trace(SCHEMA_VERSION, TraceSource.CONCEPT, "java", false, List.copyOf(steps));
    }
}
