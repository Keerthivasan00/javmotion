package com.javmotion.api.visualizer;

import com.javmotion.common.trace.EventType;
import com.javmotion.common.trace.Frame;
import com.javmotion.common.trace.HeapObject;
import com.javmotion.common.trace.Highlight;
import com.javmotion.common.trace.Trace;
import com.javmotion.common.trace.TraceStep;
import com.javmotion.common.trace.Value;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds a concept trace by "simulating" a single {@code main} frame.
 *
 * <p>Follows the same semantics as real JDI traces so the frontend treats both alike:
 * <ul>
 *   <li>A step is recorded <em>before</em> its line runs (locals show values from earlier lines).
 *   <li>A loop header line is revisited after each iteration with the old loop variable; the last
 *       revisit is the false check, followed by the line after the loop.
 *   <li>The first step is {@code CALL}, the last is {@code END} with an empty stack.
 * </ul>
 * Not thread-safe; create one per request.
 */
final class ConceptTraceBuilder {

    /** Safety net only: request validation keeps every concept far below this. */
    static final int MAX_STEPS = 2000;

    private static final String STRING_TYPE = "java.lang.String";

    private final String className;
    private final Map<String, Value> locals = new LinkedHashMap<>();
    private final Map<String, HeapObject> heap = new LinkedHashMap<>();
    private final StringBuilder stdout = new StringBuilder();
    private final List<TraceStep> steps = new ArrayList<>();
    private int nextHeapId = 1;
    private int lastLine = 1;

    ConceptTraceBuilder(String className) {
        this.className = className;
    }

    // ---------------------------------------------------------------- locals

    ConceptTraceBuilder set(String name, int value) {
        locals.put(name, Value.ofInt(value));
        return this;
    }

    ConceptTraceBuilder setRef(String name, String heapId) {
        locals.put(name, Value.ref(heapId));
        return this;
    }

    /** Variable goes out of scope (block ended). */
    ConceptTraceBuilder unset(String name) {
        locals.remove(name);
        return this;
    }

    // ---------------------------------------------------------------- heap

    String newIntArray(int[] values) {
        String id = "h" + nextHeapId++;
        heap.put(id, intArray(values));
        return id;
    }

    void updateIntArray(String id, int[] values) {
        heap.put(id, intArray(values));
    }

    String newString(String value) {
        String id = "h" + nextHeapId++;
        heap.put(id, new HeapObject.StringObj(STRING_TYPE, value, null));
        return id;
    }

    /** Object became unreachable (e.g. the old String after {@code rev = rev + c}). */
    void free(String id) {
        heap.remove(id);
    }

    // ---------------------------------------------------------------- output

    void println(String line) {
        stdout.append(line).append('\n');
    }

    // ---------------------------------------------------------------- steps

    void step(int line, Highlight... highlights) {
        if (steps.size() >= MAX_STEPS) {
            throw new IllegalStateException("Concept trace exceeded " + MAX_STEPS + " steps");
        }
        lastLine = line;
        EventType event = steps.isEmpty() ? EventType.CALL : EventType.LINE;
        Frame frame = new Frame("f1", "main", className, line, snapshot(locals));
        steps.add(new TraceStep(
                steps.size(),
                event,
                line,
                List.of(frame),
                snapshot(heap),
                stdout.toString(),
                null,
                null,
                highlights.length == 0 ? null : List.of(highlights)));
    }

    Trace build() {
        steps.add(new TraceStep(
                steps.size(), EventType.END, lastLine, List.of(), snapshot(heap), stdout.toString(), null, null, null));
        return Trace.concept(steps);
    }

    // ---------------------------------------------------------------- helpers

    static Highlight compare(String ref, int index) {
        return new Highlight(ref, index, Highlight.Role.compare, null);
    }

    static Highlight swap(String ref, int index) {
        return new Highlight(ref, index, Highlight.Role.swap, null);
    }

    static Highlight found(String ref, int index) {
        return new Highlight(ref, index, Highlight.Role.found, null);
    }

    /** Whole object (no index), e.g. the finished String. */
    static Highlight found(String ref) {
        return new Highlight(ref, null, Highlight.Role.found, null);
    }

    static Highlight active(String ref, int index, String label) {
        return new Highlight(ref, index, Highlight.Role.active, label);
    }

    static Highlight pointer(String ref, int index, String label) {
        return new Highlight(ref, index, Highlight.Role.pointer, label);
    }

    private static HeapObject intArray(int[] values) {
        return new HeapObject.ArrayObj("int[]", Arrays.stream(values).mapToObj(Value::ofInt).toList(), null);
    }

    /** Insertion-ordered, immutable copy (Map.copyOf would lose declaration order). */
    private static <V> Map<String, V> snapshot(Map<String, V> map) {
        return Collections.unmodifiableMap(new LinkedHashMap<>(map));
    }
}
