package com.javmotion.worker.sandbox;

import com.javmotion.common.job.ExecutionMode;
import com.javmotion.common.job.JobResult;
import java.io.IOException;
import java.util.UUID;

/** Compiles and runs one program inside a prepared workspace. */
public interface SandboxRunner {

    /** @param workspace holds {@code src/<className>.java}; the runner may write to {@code out/} */
    JobResult run(Workspace workspace, String className, JobSpec spec) throws IOException, InterruptedException;

    /** Mode, id and stdin of the job being run. */
    record JobSpec(ExecutionMode mode, UUID jobId, String stdin) {}
}
