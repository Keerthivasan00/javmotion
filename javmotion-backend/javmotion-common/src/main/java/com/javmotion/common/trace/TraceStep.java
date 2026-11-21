package com.javmotion.common.trace;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import java.util.Map;

/**
 * Full snapshot of program state at one step.
 *
 * @param stack  call stack; top of stack is the LAST element
 * @param heap   reachable heap objects keyed by stable id ("h1", "h2", ...)
 * @param stdout cumulative program output up to and including this step
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record TraceStep(
        int index,
        EventType event,
        int line,
        List<Frame> stack,
        Map<String, HeapObject> heap,
        String stdout,
        Value returnValue,
        TraceException exception,
        List<Highlight> highlights) {

    public record TraceException(String type, String message) {}
}
