package com.javmotion.worker.sandbox;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Runs one external process with a wall-clock timeout and bounded output capture.
 * Output is drained continuously (excess bytes are discarded, not left in the pipe), so a
 * chatty program never blocks on a full pipe and never fills worker memory.
 */
public final class ProcessRunner {

    /** What happened to the process. {@code exitCode} is null when it was killed on timeout. */
    public record Outcome(
            Integer exitCode,
            boolean timedOut,
            String stdout,
            String stderr,
            boolean outputTruncated,
            long durationMs) {}

    private ProcessRunner() {}

    /**
     * @param onTimeout extra cleanup when the timeout fires (e.g. {@code docker kill}); the process
     *     itself is always force-killed afterwards
     */
    public static Outcome run(
            List<String> command, Path workDir, String stdin, Duration timeout, int maxOutputBytes, Runnable onTimeout)
            throws IOException, InterruptedException {
        long start = System.nanoTime();
        Process process = new ProcessBuilder(command).directory(workDir.toFile()).start();

        BoundedOutput out = new BoundedOutput(maxOutputBytes);
        BoundedOutput err = new BoundedOutput(maxOutputBytes);
        Thread outPump = pump(process.getInputStream(), out, "stdout");
        Thread errPump = pump(process.getErrorStream(), err, "stderr");
        Thread inFeed = feed(process.getOutputStream(), stdin);

        boolean finished = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
        if (!finished) {
            try {
                onTimeout.run();
            } finally {
                process.descendants().forEach(ProcessHandle::destroyForcibly);
                process.destroyForcibly();
                process.waitFor(5, TimeUnit.SECONDS);
            }
        }
        // Pipes close when the process is gone; give the pumps a moment to finish draining.
        outPump.join(2000);
        errPump.join(2000);
        inFeed.interrupt();

        long durationMs = (System.nanoTime() - start) / 1_000_000;
        return new Outcome(
                finished ? process.exitValue() : null,
                !finished,
                out.text(),
                err.text(),
                out.truncated() || err.truncated(),
                durationMs);
    }

    private static Thread pump(InputStream in, OutputStream sink, String name) {
        return Thread.ofVirtual().name("pump-" + name).start(() -> {
            try (in) {
                in.transferTo(sink);
            } catch (IOException ignored) {
                // process killed mid-read: what we have is what we keep
            }
        });
    }

    private static Thread feed(OutputStream stdinPipe, String stdin) {
        return Thread.ofVirtual().name("stdin").start(() -> {
            try (stdinPipe) {
                if (stdin != null && !stdin.isEmpty()) stdinPipe.write(stdin.getBytes(StandardCharsets.UTF_8));
            } catch (IOException ignored) {
                // program exited without reading all input
            }
        });
    }
}
