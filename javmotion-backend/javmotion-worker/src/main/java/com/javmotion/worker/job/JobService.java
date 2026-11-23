package com.javmotion.worker.job;

import com.javmotion.common.job.EntryClassDetector;
import com.javmotion.common.job.JobRequest;
import com.javmotion.common.job.JobResult;
import com.javmotion.worker.sandbox.SandboxRunner;
import com.javmotion.worker.sandbox.WorkerProperties;
import com.javmotion.worker.sandbox.Workspace;
import java.io.IOException;
import java.util.concurrent.Semaphore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Runs one job end to end: concurrency slot → workspace → runner → cleanup.
 * At most {@code worker.max-concurrent} jobs run at once; extra ones are refused immediately
 * (the API retries) instead of piling up threads and memory.
 */
@Service
public class JobService {

    private static final Logger log = LoggerFactory.getLogger(JobService.class);

    private final WorkerProperties props;
    private final SandboxRunner runner;
    private final Semaphore slots;

    public JobService(WorkerProperties props, SandboxRunner runner) {
        this.props = props;
        this.runner = runner;
        this.slots = new Semaphore(props.maxConcurrent());
    }

    /** Thrown when every slot is busy; mapped to HTTP 429. */
    public static class BusyException extends RuntimeException {
        public BusyException() {
            super("All sandboxes are busy");
        }
    }

    public JobResult run(JobRequest request) {
        if (!slots.tryAcquire()) throw new BusyException();
        try (Workspace ws = Workspace.create(props.jobsDir(), request.jobId())) {
            String className = EntryClassDetector.detect(request.sourceCode());
            ws.writeSource(className, request.sourceCode());
            // Logs carry ids and outcomes only — never user code or output (doc §14).
            JobResult result = runner.run(
                    ws, className, new SandboxRunner.JobSpec(request.mode(), request.jobId(), request.stdin()));
            log.info("job {} {} -> {} in {} ms", request.jobId(), request.mode(), result.status(), result.durationMs());
            return result;
        } catch (IOException e) {
            log.error("job {} failed: {}", request.jobId(), e.toString());
            return JobResult.failed("The worker could not run this job.");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return JobResult.failed("The job was interrupted.");
        } finally {
            slots.release();
        }
    }

    int availableSlots() {
        return slots.availablePermits();
    }
}
