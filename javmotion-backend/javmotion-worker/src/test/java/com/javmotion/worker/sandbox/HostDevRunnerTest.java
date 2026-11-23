package com.javmotion.worker.sandbox;

import static org.assertj.core.api.Assertions.assertThat;

import com.javmotion.common.job.ExecutionMode;
import com.javmotion.common.job.ExecutionStatus;
import com.javmotion.common.job.JobResult;
import java.nio.file.Path;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Runs real javac/java from the test JDK — the whole host pipeline, minus the container. */
class HostDevRunnerTest {

    @TempDir
    Path jobsDir;

    private static final WorkerProperties PROPS = new WorkerProperties(
            "t", "img", 2, Path.of("."),
            new WorkerProperties.Limits("256m", "1", 64, "16m", Duration.ofSeconds(3), Duration.ofSeconds(10), 1024));

    private JobResult run(String className, String code, String stdin) throws Exception {
        UUID id = UUID.randomUUID();
        try (Workspace ws = Workspace.create(jobsDir, id)) {
            ws.writeSource(className, code);
            return new HostDevRunner(PROPS).run(ws, className, new SandboxRunner.JobSpec(ExecutionMode.RUN, id, stdin));
        }
    }

    @Test
    void runsAProgram() throws Exception {
        JobResult r = run("Main", """
                public class Main {
                    public static void main(String[] args) {
                        System.out.println("hello " + (2 + 3));
                    }
                }
                """, null);
        assertThat(r.status()).isEqualTo(ExecutionStatus.COMPLETED);
        assertThat(r.stdout().strip()).isEqualTo("hello 5");
        assertThat(r.exitCode()).isZero();
    }

    @Test
    void reportsCompileErrorsWithPositions() throws Exception {
        JobResult r = run("Main", """
                public class Main {
                    public static void main(String[] args) {
                        int x = 5
                    }
                }
                """, null);
        assertThat(r.status()).isEqualTo(ExecutionStatus.COMPILE_ERROR);
        assertThat(r.compileErrors()).first().satisfies(e -> {
            assertThat(e.line()).isEqualTo(3);
            assertThat(e.message()).isEqualTo("';' expected");
        });
        assertThat(r.exitCode()).isNull();
    }

    @Test
    void uncaughtExceptionIsARuntimeError() throws Exception {
        JobResult r = run("Boom", """
                public class Boom {
                    public static void main(String[] args) {
                        int[] a = new int[1];
                        a[3] = 1;
                    }
                }
                """, null);
        assertThat(r.status()).isEqualTo(ExecutionStatus.RUNTIME_ERROR);
        assertThat(r.stderr()).contains("ArrayIndexOutOfBoundsException");
        assertThat(r.exitCode()).isEqualTo(1);
    }

    @Test
    void infiniteLoopTimesOut() throws Exception {
        JobResult r = run("Main", """
                public class Main {
                    public static void main(String[] args) {
                        while (true) {}
                    }
                }
                """, null);
        assertThat(r.status()).isEqualTo(ExecutionStatus.TIMEOUT);
        assertThat(r.errorMessage()).contains("3 s");
    }

    @Test
    void outputFloodIsCutAtTheLimit() throws Exception {
        JobResult r = run("Main", """
                public class Main {
                    public static void main(String[] args) {
                        for (int i = 0; i < 100_000; i++) System.out.println("line " + i);
                    }
                }
                """, null);
        assertThat(r.status()).isEqualTo(ExecutionStatus.LIMIT_EXCEEDED);
        assertThat(r.outputTruncated()).isTrue();
        assertThat(r.stdout().length()).isLessThanOrEqualTo(1024);
    }

    @Test
    void readsStdin() throws Exception {
        JobResult r = run("Main", """
                import java.util.Scanner;
                public class Main {
                    public static void main(String[] args) {
                        Scanner in = new Scanner(System.in);
                        System.out.println(in.nextInt() * 2);
                    }
                }
                """, "21\n");
        assertThat(r.status()).isEqualTo(ExecutionStatus.COMPLETED);
        assertThat(r.stdout().strip()).isEqualTo("42");
    }

    @Test
    void visualizeIsNotAvailableYet() throws Exception {
        UUID id = UUID.randomUUID();
        try (Workspace ws = Workspace.create(jobsDir, id)) {
            ws.writeSource("Main", "public class Main {}");
            JobResult r = new HostDevRunner(PROPS)
                    .run(ws, "Main", new SandboxRunner.JobSpec(ExecutionMode.VISUALIZE, id, null));
            assertThat(r.status()).isEqualTo(ExecutionStatus.FAILED);
        }
    }
}
