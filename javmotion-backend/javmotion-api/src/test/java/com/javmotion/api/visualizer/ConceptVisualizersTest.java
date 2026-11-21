package com.javmotion.api.visualizer;

import static com.javmotion.api.visualizer.ConceptTraceAssertions.assertWellFormed;
import static com.javmotion.api.visualizer.ConceptTraceAssertions.finalArray;
import static com.javmotion.api.visualizer.ConceptTraceAssertions.finalStdout;
import static com.javmotion.api.visualizer.ConceptTraceAssertions.lines;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.javmotion.api.common.error.ApiException;
import com.javmotion.api.visualizer.ArrayConceptVisualizer.Operation;
import com.javmotion.api.visualizer.ConceptVisualizer.ConceptResult;
import com.javmotion.api.visualizer.LoopsVisualizer.LoopType;
import com.javmotion.common.trace.HeapObject;
import com.javmotion.common.trace.TraceStep;
import com.javmotion.common.trace.Value;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

class ConceptVisualizersTest {

    private final StringReverseVisualizer strings = new StringReverseVisualizer();
    private final ArrayConceptVisualizer arrays = new ArrayConceptVisualizer();
    private final LoopsVisualizer loops = new LoopsVisualizer();

    @Nested
    class StringReverse {

        @Test
        void reversesAndCreatesANewStringPerConcat() {
            ConceptResult r = strings.generate(new StringReverseVisualizer.Request("code"));
            assertWellFormed(r);
            assertThat(finalStdout(r)).isEqualTo("edoc\n");
            assertThat(r.code()).contains("String s = \"code\";");

            Set<String> revRefs = r.trace().steps().stream()
                    .filter(s -> !s.stack().isEmpty())
                    .map(s -> s.stack().getFirst().locals().get("rev"))
                    .filter(v -> v instanceof Value.Ref)
                    .map(v -> ((Value.Ref) v).ref())
                    .collect(Collectors.toSet());
            assertThat(revRefs).hasSize(5); // "" + 4 concatenations
        }

        @Test
        void loopShapeMatchesJdi() {
            ConceptResult r = strings.generate(new StringReverseVisualizer.Request("ab"));
            assertThat(lines(r)).containsExactly(3, 4, 5, 6, 5, 6, 5, 8, 8);
        }

        @Test
        void escapesQuotesInCode() {
            ConceptResult r = strings.generate(new StringReverseVisualizer.Request("a\"b\\"));
            assertThat(r.code()).contains("String s = \"a\\\"b\\\\\";");
            assertThat(finalStdout(r)).isEqualTo("\\b\"a\n");
        }
    }

    @Nested
    class Arrays_ {

        @ParameterizedTest
        @EnumSource(value = Operation.class, names = {"BUBBLE_SORT", "SELECTION_SORT"})
        void sortsCorrectly(Operation op) {
            ConceptResult r = arrays.generate(new ArrayConceptVisualizer.Request(op, List.of(5, 1, 4, 2, 8, 0), null));
            assertWellFormed(r);
            assertThat(finalArray(r)).containsExactly(0, 1, 2, 4, 5, 8);
            assertThat(finalStdout(r)).isEqualTo("[0, 1, 2, 4, 5, 8]\n");
        }

        @RepeatedTest(25)
        void randomInputsStayWellFormed() {
            Random random = new Random();
            int n = 1 + random.nextInt(ArrayConceptVisualizer.MAX_VALUES);
            List<Integer> values = random.ints(n, -999, 1000).boxed().toList();
            int[] sorted = values.stream().mapToInt(Integer::intValue).sorted().toArray();
            int target = values.get(random.nextInt(n));

            for (Operation op : Operation.values()) {
                List<Integer> in = op == Operation.BINARY_SEARCH ? Arrays.stream(sorted).boxed().toList() : values;
                ConceptResult r = arrays.generate(new ArrayConceptVisualizer.Request(op, in, target));
                assertWellFormed(r);
                assertThat(r.trace().steps()).hasSizeLessThan(ConceptTraceBuilder.MAX_STEPS);
                if (op == Operation.BUBBLE_SORT || op == Operation.SELECTION_SORT) {
                    assertThat(finalArray(r)).containsExactly(sorted);
                } else {
                    int idx = Integer.parseInt(finalStdout(r).trim());
                    assertThat(in.get(idx)).isEqualTo(target);
                }
            }
        }

        @Test
        void bubbleSortSwapUsesTempAndSwapHighlights() {
            ConceptResult r = arrays.generate(new ArrayConceptVisualizer.Request(Operation.BUBBLE_SORT, List.of(2, 1), null));
            TraceStep atLine9 = r.trace().steps().stream().filter(s -> s.line() == 9).findFirst().orElseThrow();
            assertThat(atLine9.stack().getFirst().locals()).containsKey("temp");
            assertThat(atLine9.highlights()).anyMatch(h -> h.role().name().equals("swap"));
        }

        @ParameterizedTest
        @CsvSource({"LINEAR_SEARCH,4,2", "LINEAR_SEARCH,8,-1", "LINEAR_SEARCH,9,4", "BINARY_SEARCH,7,3", "BINARY_SEARCH,6,-1", "BINARY_SEARCH,1,0"})
        void searchFindsIndexOrMinusOne(Operation op, int target, int expected) {
            ConceptResult r = arrays.generate(new ArrayConceptVisualizer.Request(op, List.of(1, 3, 4, 7, 9), target));
            assertWellFormed(r);
            assertThat(finalStdout(r)).isEqualTo(expected + "\n");
        }

        @Test
        void binarySearchPointersAreLabelled() {
            ConceptResult r = arrays.generate(
                    new ArrayConceptVisualizer.Request(Operation.BINARY_SEARCH, List.of(1, 3, 4, 7, 9), 9));
            Set<String> labels = r.trace().steps().stream()
                    .filter(s -> s.highlights() != null)
                    .flatMap(s -> s.highlights().stream())
                    .map(h -> h.label() == null ? "" : h.label())
                    .collect(Collectors.toSet());
            assertThat(labels).contains("low", "mid", "high");
        }

        @Test
        void searchWithoutTargetIsRejected() {
            assertThatThrownBy(() -> arrays.generate(
                            new ArrayConceptVisualizer.Request(Operation.LINEAR_SEARCH, List.of(1), null)))
                    .isInstanceOf(ApiException.class)
                    .hasFieldOrPropertyWithValue("code", "TARGET_REQUIRED");
        }

        @Test
        void binarySearchOnUnsortedIsRejected() {
            assertThatThrownBy(() -> arrays.generate(
                            new ArrayConceptVisualizer.Request(Operation.BINARY_SEARCH, List.of(3, 1), 1)))
                    .isInstanceOf(ApiException.class)
                    .hasFieldOrPropertyWithValue("code", "ARRAY_NOT_SORTED");
        }

        @Test
        void sortDoesNotMutateTheRequest() {
            List<Integer> values = List.of(3, 2, 1);
            ConceptResult r = arrays.generate(new ArrayConceptVisualizer.Request(Operation.BUBBLE_SORT, values, null));
            HeapObject.ArrayObj first = (HeapObject.ArrayObj) r.trace().steps().get(1).heap().values().iterator().next();
            assertThat(first.elements()).extracting(v -> ((Value.Primitive) v).value()).containsExactly(3, 2, 1);
            assertThat(r.code()).contains("{3, 2, 1}");
        }
    }

    @Nested
    class Loops {

        @Test
        void forLoopLineShapeMatchesJdi() {
            ConceptResult r = loops.generate(new LoopsVisualizer.Request(LoopType.FOR, 1, 3, 1));
            assertWellFormed(r);
            assertThat(lines(r)).containsExactly(3, 4, 3, 4, 3, 4, 3, 6, 6);
            assertThat(finalStdout(r)).isEqualTo("i = 1\ni = 2\ni = 3\ndone\n");
            assertThat(r.code()).contains("for (int i = 1; i <= 3; i++) {");
        }

        @Test
        void countsDownWhenStartIsAboveEnd() {
            ConceptResult r = loops.generate(new LoopsVisualizer.Request(LoopType.WHILE, 10, 4, 3));
            assertWellFormed(r);
            assertThat(finalStdout(r)).isEqualTo("i = 10\ni = 7\ni = 4\ndone\n");
            assertThat(r.code()).contains("while (i >= 4) {").contains("i -= 3;");
        }

        @Test
        void doWhileChecksAtTheBottom() {
            ConceptResult r = loops.generate(new LoopsVisualizer.Request(LoopType.DO_WHILE, 0, 1, null));
            assertWellFormed(r);
            assertThat(lines(r)).containsExactly(3, 5, 6, 7, 5, 6, 7, 8, 8);
        }

        @Test
        void nestedLoopRunsInnerThreeTimesPerOuter() {
            ConceptResult r = loops.generate(new LoopsVisualizer.Request(LoopType.NESTED, 1, 2, 1));
            assertWellFormed(r);
            assertThat(finalStdout(r).lines()).hasSize(7).first().isEqualTo("1 x 1 = 1");
        }

        @ParameterizedTest
        @EnumSource(LoopType.class)
        void allTypesWellFormedAtTheLimit(LoopType type) {
            int end = type == LoopType.NESTED ? LoopsVisualizer.MAX_NESTED_OUTER : LoopsVisualizer.MAX_ITERATIONS;
            assertWellFormed(loops.generate(new LoopsVisualizer.Request(type, 1, end, 1)));
        }

        @Test
        void tooManyIterationsIsRejected() {
            assertThatThrownBy(() -> loops.generate(new LoopsVisualizer.Request(LoopType.FOR, -100, 100, 1)))
                    .isInstanceOf(ApiException.class)
                    .hasFieldOrPropertyWithValue("code", "TOO_MANY_ITERATIONS");
        }
    }
}
