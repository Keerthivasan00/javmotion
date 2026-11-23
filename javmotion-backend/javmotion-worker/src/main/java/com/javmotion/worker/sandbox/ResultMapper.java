package com.javmotion.worker.sandbox;

import com.javmotion.common.job.CompileError;
import com.javmotion.common.job.ExecutionStatus;
import com.javmotion.common.job.JavacErrorParser;
import com.javmotion.common.job.JobResult;
import java.time.Duration;
import java.util.List;

/** Turns a finished (or killed) process into the status from backend doc §4.2. */
public final class ResultMapper {

    /** Exit code of a container killed by the kernel OOM killer (128 + SIGKILL). */
    static final int OOM_KILLED = 137;

    private ResultMapper() {}

    /**
     * @param compileOutput javac stderr when compilation failed, else null
     * @param oomAware      true for Docker: exit 137 means the memory limit was hit
     */
    public static JobResult map(
            ProcessRunner.Outcome run, String compileOutput, Duration timeout, int maxOutputBytes, boolean oomAware) {
        if (compileOutput != null) {
            List<CompileError> errors = JavacErrorParser.parse(compileOutput);
            return new JobResult(ExecutionStatus.COMPILE_ERROR, "", compileOutput, errors, null,
                    run.durationMs(), false, null, null);
        }
        if (run.timedOut()) {
            return result(run, ExecutionStatus.TIMEOUT, "Time limit of " + timeout.toSeconds() + " s exceeded.");
        }
        if (oomAware && run.exitCode() != null && run.exitCode() == OOM_KILLED) {
            return result(run, ExecutionStatus.LIMIT_EXCEEDED, "Memory limit exceeded.");
        }
        if (run.outputTruncated()) {
            return result(run, ExecutionStatus.LIMIT_EXCEEDED,
                    "Output limit of " + (maxOutputBytes / 1024) + " KB exceeded; the rest was cut.");
        }
        ExecutionStatus status = run.exitCode() != null && run.exitCode() == 0
                ? ExecutionStatus.COMPLETED
                : ExecutionStatus.RUNTIME_ERROR;
        return result(run, status, null);
    }

    private static JobResult result(ProcessRunner.Outcome run, ExecutionStatus status, String message) {
        return new JobResult(status, run.stdout(), run.stderr(), List.of(), run.exitCode(), run.durationMs(),
                run.outputTruncated(), message, null);
    }
}
