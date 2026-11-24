package com.javmotion.tracer;

import static org.assertj.core.api.Assertions.assertThat;

import com.javmotion.common.trace.EventType;
import com.javmotion.common.trace.Frame;
import com.javmotion.common.trace.HeapObject;
import com.javmotion.common.trace.Trace;
import com.javmotion.common.trace.TraceSource;
import com.javmotion.common.trace.TraceStep;
import com.javmotion.common.trace.Value;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

/** Real JDI runs of small programs — the same samples as the B0 spike. */
@Timeout(60) // a hang here must fail the build, not stall it
class TraceRecorderTest {

    @TempDir
    Path dir;

    private TraceRecorder.Recording record(String source, TraceLimits limits) throws Exception {
        Path src = dir.resolve("Main.java");
        Files.writeString(src, source);
        Path classes = Files.createDirectories(dir.resolve("classes"));
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        int rc = ToolProvider.getSystemJavaCompiler().run(null, null, err, "-g", "-d", classes.toString(), src.toString());
        assertThat(rc).as("javac: %s", err).isZero();
        return new TraceRecorder(limits).record(classes.toString(), "Main");
    }

    private TraceRecorder.Recording record(String source) throws Exception {
        return record(source, TraceLimits.defaults());
    }

    private static List<Integer> lines(Trace t) {
        return t.steps().stream().map(TraceStep::line).toList();
    }

    private static Frame top(TraceStep s) {
        return s.stack().getLast();
    }

    private static String nl(String s) {
        return s.replace("\r\n", "\n");
    }

    @Test
    void forLoopHasTheSameShapeAsTheFixtures() throws Exception {
        TraceRecorder.Recording r = record("""
                public class Main {
                    public static void main(String[] args) {
                        int sum = 0;
                        for (int i = 1; i <= 3; i++) {
                            sum += i;
                            System.out.println("sum = " + sum);
                        }
                        System.out.println("done");
                    }
                }
                """);
        Trace t = r.trace();

        assertThat(t.source()).isEqualTo(TraceSource.JDI);
        assertThat(t.truncated()).isFalse();
        assertThat(lines(t)).containsExactly(3, 4, 5, 6, 4, 5, 6, 4, 5, 6, 4, 8, 9, 9, 9);
        assertThat(t.steps().getFirst().event()).isEqualTo(EventType.CALL);
        assertThat(t.steps().get(t.steps().size() - 2).event()).isEqualTo(EventType.RETURN);
        assertThat(t.steps().getLast().event()).isEqualTo(EventType.END);
        for (int i = 0; i < t.steps().size(); i++) assertThat(t.steps().get(i).index()).isEqualTo(i);

        // stdout is cumulative and lands on the step after the println ran
        assertThat(nl(t.steps().get(3).stdout())).isEmpty();
        assertThat(nl(t.steps().get(4).stdout())).isEqualTo("sum = 1\n");
        assertThat(nl(t.steps().getLast().stdout())).isEqualTo("sum = 1\nsum = 3\nsum = 6\ndone\n");
        assertThat(nl(r.stdout())).isEqualTo("sum = 1\nsum = 3\nsum = 6\ndone\n");
        assertThat(r.exitCode()).isZero();

        assertThat(top(t.steps().get(3)).locals())
                .containsEntry("sum", Value.ofInt(1))
                .containsEntry("i", Value.ofInt(1));
    }

    @Test
    void recursionGetsOneFrameIdPerCallAndReturnValues() throws Exception {
        Trace t = record("""
                public class Main {
                    static int factorial(int n) {
                        if (n <= 1) {
                            return 1;
                        }
                        return n * factorial(n - 1);
                    }

                    public static void main(String[] args) {
                        int result = factorial(4);
                        System.out.println("4! = " + result);
                    }
                }
                """).trace();

        assertThat(t.steps().stream().mapToInt(s -> s.stack().size()).max().orElse(0)).isEqualTo(5);
        Set<String> ids = t.steps().stream().flatMap(s -> s.stack().stream()).map(Frame::id).collect(Collectors.toSet());
        assertThat(ids).hasSize(5);
        List<Object> returns = t.steps().stream()
                .filter(s -> s.event() == EventType.RETURN && s.returnValue() != null)
                .map(s -> ((Value.Primitive) s.returnValue()).value())
                .toList();
        assertThat(returns).containsExactly(1, 2, 6, 24);
    }

    @Test
    void uncaughtExceptionIsRecordedAndExitCodeIsKept() throws Exception {
        TraceRecorder.Recording r = record("""
                public class Main {
                    public static void main(String[] args) {
                        int[] a = {1, 2, 3};
                        int total = 0;
                        for (int i = 0; i <= a.length; i++) {
                            total += a[i];
                        }
                        System.out.println(total);
                    }
                }
                """);
        TraceStep ex = r.trace().steps().stream().filter(s -> s.event() == EventType.EXCEPTION).findFirst().orElseThrow();
        assertThat(ex.exception().type()).isEqualTo("java.lang.ArrayIndexOutOfBoundsException");
        assertThat(ex.exception().message()).isEqualTo("Index 3 out of bounds for length 3");
        assertThat(ex.line()).isEqualTo(6);
        assertThat(r.exitCode()).isEqualTo(1);
        assertThat(r.stderr()).contains("ArrayIndexOutOfBoundsException");
    }

    @Test
    void heapKeepsStableIdsHandlesCyclesAndPreviewsBigArrays() throws Exception {
        Trace t = record("""
                public class Main {
                    static class Node {
                        int value;
                        Node next;
                        Node(int value) { this.value = value; }
                    }

                    public static void main(String[] args) {
                        Node head = new Node(1);
                        head.next = new Node(2);
                        head.next.next = head;
                        int[] big = new int[150];
                        double ratio = 0.0 / 0.0;
                        System.out.println(head.next.value);
                    }
                }
                """).trace();
        TraceStep last = t.steps().stream().filter(s -> s.line() == 14 && s.event() == EventType.LINE).findFirst().orElseThrow();
        String headRef = ((Value.Ref) top(last).locals().get("head")).ref();
        HeapObject.ObjectObj head = (HeapObject.ObjectObj) last.heap().get(headRef);
        String nextRef = ((Value.Ref) head.fields().get("next")).ref();
        HeapObject.ObjectObj next = (HeapObject.ObjectObj) last.heap().get(nextRef);
        assertThat(next.fields().get("next")).isEqualTo(Value.ref(headRef)); // cycle back

        HeapObject.ArrayObj big = (HeapObject.ArrayObj) last.heap().get(((Value.Ref) top(last).locals().get("big")).ref());
        assertThat(big.elements()).hasSize(100);
        assertThat(big.length()).isEqualTo(150);
        assertThat(top(last).locals().get("ratio")).isEqualTo(new Value.Primitive("double", "NaN"));

        // head keeps the same id on every step where it exists
        Set<String> headIds = t.steps().stream()
                .filter(s -> !s.stack().isEmpty() && top(s).locals().get("head") instanceof Value.Ref)
                .map(s -> ((Value.Ref) top(s).locals().get("head")).ref())
                .collect(Collectors.toSet());
        assertThat(headIds).containsExactly(headRef);
    }

    @Test
    void stepLimitTruncatesButKeepsAValidTrace() throws Exception {
        TraceRecorder.Recording r = record("""
                public class Main {
                    public static void main(String[] args) {
                        long sum = 0;
                        for (int i = 0; i < 1_000_000; i++) {
                            sum += i;
                        }
                        System.out.println(sum);
                    }
                }
                """, TraceLimits.defaults().withMaxSteps(200));
        Trace t = r.trace();
        assertThat(t.truncated()).isTrue();
        assertThat(t.steps()).hasSizeLessThanOrEqualTo(200);
        assertThat(t.steps().getLast().event()).isEqualTo(EventType.END);
    }

    @Test
    void timeLimitTruncatesInsteadOfHanging() throws Exception {
        long start = System.currentTimeMillis();
        TraceRecorder.Recording r = record("""
                public class Main {
                    public static void main(String[] args) {
                        int x = 0;
                        while (true) {
                            x++;
                        }
                    }
                }
                """, TraceLimits.defaults().withMaxMillis(1_000));
        assertThat(r.trace().truncated()).isTrue();
        assertThat(System.currentTimeMillis() - start).isLessThan(15_000);
    }

    @Test
    void stdinIsClosedSoReadingDoesNotHang() throws Exception {
        TraceRecorder.Recording r = record("""
                public class Main {
                    public static void main(String[] args) throws Exception {
                        int c = System.in.read();
                        System.out.println("read " + c);
                    }
                }
                """, TraceLimits.defaults().withMaxMillis(5_000));
        assertThat(nl(r.stdout())).isEqualTo("read -1\n");
        assertThat(r.trace().truncated()).isFalse();
    }
}
