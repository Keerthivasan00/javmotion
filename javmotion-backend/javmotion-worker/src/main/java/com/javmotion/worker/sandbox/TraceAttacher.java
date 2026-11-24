package com.javmotion.worker.sandbox;

import com.javmotion.common.job.ExecutionStatus;
import com.javmotion.common.job.JobResult;
import com.javmotion.common.trace.Trace;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * Adds the tracer's {@code trace.json} to a VISUALIZE result. The file came out of the sandbox,
 * so it is untrusted: size-checked and parsed into the trace model before it is passed on
 * (doc §6.4, "re-checked in worker").
 */
public final class TraceAttacher {

    private static final Logger log = LoggerFactory.getLogger(TraceAttacher.class);

    /** Tracer cap is 10 MB; allow a little slack for the final END step. */
    static final long MAX_TRACE_BYTES = 11L * 1024 * 1024;

    /** Exit code the tracer uses when it failed itself (TracerMain.TRACER_FAILED). */
    static final int TRACER_FAILED = 70;

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private TraceAttacher() {}

    public static JobResult attach(JobResult run, Path traceFile) {
        // No trace to show: javac failed, or the whole process was killed on the wall clock.
        if (run.status() == ExecutionStatus.COMPILE_ERROR || run.status() == ExecutionStatus.TIMEOUT) return run;

        try {
            if (!Files.exists(traceFile)) {
                if (run.exitCode() != null && run.exitCode() == TRACER_FAILED) {
                    log.warn("tracer failed: {}", firstLine(run.stderr()));
                    return JobResult.failed("Could not record this program for visualization.");
                }
                return JobResult.failed("The visualizer produced no trace.");
            }
            long size = Files.size(traceFile);
            if (size > MAX_TRACE_BYTES) {
                return with(run, ExecutionStatus.LIMIT_EXCEEDED, "The trace is too large to show.", null);
            }
            String json = Files.readString(traceFile, StandardCharsets.UTF_8);
            Trace trace = JSON.readValue(json, Trace.class); // validates the shape
            if (trace.steps().isEmpty()) return JobResult.failed("The visualizer produced an empty trace.");

            if (trace.truncated()) {
                // The tracer stopped the program on a step / size / time limit (its exit code is ours).
                return with(run, ExecutionStatus.LIMIT_EXCEEDED,
                        "Visualization stopped after " + trace.steps().size()
                                + " steps (step, size or time limit). The steps so far are shown.",
                        json);
            }
            return with(run, run.status(), run.errorMessage(), json);
        } catch (IOException | JacksonException e) {
            log.warn("invalid trace.json: {}", e.toString());
            return JobResult.failed("The visualizer produced an unreadable trace.");
        }
    }

    private static JobResult with(JobResult r, ExecutionStatus status, String message, String traceJson) {
        return new JobResult(status, r.stdout(), r.stderr(), r.compileErrors(), r.exitCode(), r.durationMs(),
                r.outputTruncated(), message, traceJson);
    }

    private static String firstLine(String s) {
        if (s == null) return "";
        int nl = s.indexOf('\n');
        return nl < 0 ? s : s.substring(0, nl);
    }
}
