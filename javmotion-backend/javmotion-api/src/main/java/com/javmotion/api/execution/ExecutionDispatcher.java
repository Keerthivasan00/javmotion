package com.javmotion.api.execution;

import com.javmotion.api.config.AsyncConfig;
import com.javmotion.common.job.ExecutionStatus;
import com.javmotion.common.job.JobRequest;
import com.javmotion.common.job.JobResult;
import com.javmotion.common.trace.Trace;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.UUID;
import java.util.zip.GZIPOutputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * Background half of a run: QUEUED → RUNNING → call worker → terminal status (doc §4.1).
 * Each state change is its own short transaction (repository save), so the HTTP request that
 * created the execution has long returned and the client is already polling.
 */
@Component
public class ExecutionDispatcher {

    private static final Logger log = LoggerFactory.getLogger(ExecutionDispatcher.class);

    static final int MAX_ATTEMPTS = 3;

    private final ExecutionRepository executions;
    private final ExecutionTraceRepository traces;
    private final WorkerClient worker;
    private final JsonMapper json;
    private final Clock clock;
    private final Duration busyBackoff;

    @Autowired
    public ExecutionDispatcher(
            ExecutionRepository executions,
            ExecutionTraceRepository traces,
            WorkerClient worker,
            JsonMapper json,
            Clock clock) {
        this(executions, traces, worker, json, clock, Duration.ofSeconds(1));
    }

    ExecutionDispatcher(
            ExecutionRepository executions,
            ExecutionTraceRepository traces,
            WorkerClient worker,
            JsonMapper json,
            Clock clock,
            Duration busyBackoff) {
        this.executions = executions;
        this.traces = traces;
        this.worker = worker;
        this.json = json;
        this.clock = clock;
        this.busyBackoff = busyBackoff;
    }

    @Async(AsyncConfig.EXECUTION_EXECUTOR)
    public void dispatch(String executionId) {
        Execution e = executions.findById(executionId).orElse(null);
        if (e == null || e.getStatus() != ExecutionStatus.QUEUED) return; // deleted or cancelled meanwhile
        e.start(clock.instant());
        executions.save(e);

        JobResult result;
        try {
            result = callWorker(new JobRequest(UUID.fromString(e.getId()), e.getMode(), e.getSourceCode(), e.getStdin()));
        } catch (RuntimeException ex) {
            log.warn("execution {} dispatch failed: {}", executionId, ex.getMessage());
            result = JobResult.failed(ex instanceof WorkerClient.WorkerBusyException
                    ? "The server is busy. Please try again in a moment."
                    : "The code runner is not available right now.");
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            result = JobResult.failed("Interrupted.");
        }
        complete(executionId, result);
    }

    private JobResult callWorker(JobRequest request) throws InterruptedException {
        for (int attempt = 1; ; attempt++) {
            try {
                return worker.run(request);
            } catch (WorkerClient.WorkerBusyException busy) {
                if (attempt >= MAX_ATTEMPTS) throw busy;
                Thread.sleep(busyBackoff.multipliedBy(attempt));
            }
        }
    }

    private void complete(String executionId, JobResult r) {
        // Reload: the user may have cancelled while the worker was running.
        Execution e = executions.findById(executionId).orElse(null);
        if (e == null || e.getStatus() != ExecutionStatus.RUNNING) return;

        if (r.status() == ExecutionStatus.FAILED) {
            e.fail(r.errorMessage(), clock.instant());
        } else {
            e.finish(new ExecutionResult(
                            r.status(),
                            r.stdout(),
                            r.stderr(),
                            r.compileErrors().isEmpty() ? null : json.writeValueAsString(r.compileErrors()),
                            r.exitCode(),
                            (int) Math.min(Integer.MAX_VALUE, r.durationMs()),
                            r.errorMessage()),
                    clock.instant());
        }
        executions.save(e);

        if (r.traceJson() != null) {
            Trace trace = json.readValue(r.traceJson(), Trace.class);
            traces.save(new ExecutionTrace(
                    executionId, trace.schemaVersion(), trace.steps().size(), trace.truncated(),
                    gzip(r.traceJson()), clock.instant()));
        }
    }

    static byte[] gzip(String text) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (GZIPOutputStream gz = new GZIPOutputStream(bytes)) {
            gz.write(text.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return bytes.toByteArray();
    }
}
