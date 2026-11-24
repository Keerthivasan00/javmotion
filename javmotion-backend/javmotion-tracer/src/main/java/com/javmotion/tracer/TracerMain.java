package com.javmotion.tracer;

import java.nio.file.Files;
import java.nio.file.Path;
import tools.jackson.databind.json.JsonMapper;

/**
 * Entry point inside the sandbox:
 * {@code java -jar javmotion-tracer.jar <classpath> <MainClass> <out/trace.json> [maxMillis]}.
 *
 * <p>Contract with the worker:
 * <ul>
 *   <li>The program's stdout / stderr are passed through to this process's stdout / stderr.
 *   <li>Exit code = the program's exit code, so the worker maps it like a normal RUN.
 *   <li>{@code trace.json} is written whenever tracing worked (also when truncated by a limit).
 *   <li>Exit code {@value #TRACER_FAILED} with no trace file = the tracer itself failed.
 * </ul>
 */
public final class TracerMain {

    /** EX_SOFTWARE: internal tracer error, distinct from anything the user program returns normally. */
    public static final int TRACER_FAILED = 70;

    private TracerMain() {}

    public static void main(String[] args) {
        if (args.length < 3 || args.length > 4) {
            System.err.println("Usage: java -jar javmotion-tracer.jar <classpath> <MainClass> <out.json> [maxMillis]");
            System.exit(2);
        }
        TraceLimits limits = TraceLimits.defaults();
        if (args.length == 4) limits = limits.withMaxMillis(Long.parseLong(args[3]));

        TraceRecorder.Recording r;
        try {
            r = new TraceRecorder(limits).record(args[0], args[1]);
            Files.write(Path.of(args[2]), JsonMapper.builder().build().writeValueAsBytes(r.trace()));
        } catch (Exception e) {
            System.err.println("javmotion-tracer failed: " + e);
            System.exit(TRACER_FAILED);
            return;
        }
        System.out.print(r.stdout());
        System.err.print(r.stderr());
        System.out.flush();
        System.err.flush();
        System.exit(r.exitCode());
    }
}
