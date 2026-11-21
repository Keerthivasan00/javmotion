package com.javmotion.api.visualizer;

import com.javmotion.common.trace.Trace;

/**
 * Generates a trace for a predefined concept in pure Java — no user code runs.
 * Traces use the same schema (and the same step semantics) as JDI traces, so the frontend
 * visualizers work for both.
 *
 * @param <I> validated, size-capped request
 */
public interface ConceptVisualizer<I> {

    /** URL segment, e.g. {@code "string-reverse"}. */
    String key();

    ConceptResult generate(I input);

    /**
     * @param code Java program the trace's line numbers refer to (shown in the editor and used
     *     by the frontend to read loop headers)
     */
    record ConceptResult(String key, String title, String code, Trace trace) {}
}
