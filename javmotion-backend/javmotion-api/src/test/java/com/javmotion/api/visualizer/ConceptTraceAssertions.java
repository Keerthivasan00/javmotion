package com.javmotion.api.visualizer;

import static org.assertj.core.api.Assertions.assertThat;

import com.javmotion.api.visualizer.ConceptVisualizer.ConceptResult;
import com.javmotion.common.trace.EventType;
import com.javmotion.common.trace.HeapObject;
import com.javmotion.common.trace.Highlight;
import com.javmotion.common.trace.TraceSource;
import com.javmotion.common.trace.TraceStep;
import com.javmotion.common.trace.Value;
import java.util.List;

/** Invariants every concept trace must satisfy so the frontend can play it like a JDI trace. */
final class ConceptTraceAssertions {

    private ConceptTraceAssertions() {}

    static void assertWellFormed(ConceptResult result) {
        String[] code = result.code().split("\n", -1);
        List<TraceStep> steps = result.trace().steps();

        assertThat(result.trace().source()).isEqualTo(TraceSource.CONCEPT);
        assertThat(steps).hasSizeGreaterThan(1);
        assertThat(steps.getFirst().event()).isEqualTo(EventType.CALL);
        assertThat(steps.getLast().event()).isEqualTo(EventType.END);
        assertThat(steps.getLast().stack()).isEmpty();

        String prevStdout = "";
        for (int i = 0; i < steps.size(); i++) {
            TraceStep s = steps.get(i);
            assertThat(s.index()).isEqualTo(i);
            assertThat(s.stdout()).as("stdout only grows (step %d)", i).startsWith(prevStdout);
            prevStdout = s.stdout();
            if (s.event() == EventType.END) {
                assertThat(i).isEqualTo(steps.size() - 1);
                continue;
            }

            assertThat(s.line()).as("step %d line in code", i).isBetween(1, code.length);
            String text = code[s.line() - 1].trim();
            assertThat(text).as("step %d points at executable code: '%s'", i, text).isNotEmpty().isNotEqualTo("}");

            assertThat(s.stack()).hasSize(1);
            assertThat(s.stack().getFirst().line()).isEqualTo(s.line());
            for (Value v : s.stack().getFirst().locals().values()) {
                if (v instanceof Value.Ref ref) {
                    assertThat(s.heap()).as("step %d local ref %s exists", i, ref.ref()).containsKey(ref.ref());
                }
            }

            if (s.highlights() != null) {
                for (Highlight h : s.highlights()) {
                    HeapObject obj = s.heap().get(h.ref());
                    assertThat(obj).as("step %d highlight ref %s exists", i, h.ref()).isNotNull();
                    if (h.index() != null) {
                        int size = switch (obj) {
                            case HeapObject.ArrayObj a -> a.elements().size();
                            case HeapObject.StringObj str -> str.value().length();
                            default -> 0;
                        };
                        assertThat(h.index()).as("step %d highlight index", i).isBetween(0, size - 1);
                    }
                }
            }
        }
    }

    static List<Integer> lines(ConceptResult result) {
        return result.trace().steps().stream().map(TraceStep::line).toList();
    }

    static String finalStdout(ConceptResult result) {
        return result.trace().steps().getLast().stdout();
    }

    static int[] finalArray(ConceptResult result) {
        TraceStep last = result.trace().steps().getLast();
        HeapObject.ArrayObj arr = (HeapObject.ArrayObj) last.heap().values().stream()
                .filter(o -> o instanceof HeapObject.ArrayObj)
                .findFirst()
                .orElseThrow();
        return arr.elements().stream().mapToInt(v -> (Integer) ((Value.Primitive) v).value()).toArray();
    }
}
