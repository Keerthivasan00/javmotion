package com.javmotion.worker.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.javmotion.common.job.ExecutionMode;
import com.javmotion.common.job.ExecutionStatus;
import com.javmotion.common.job.JobRequest;
import com.javmotion.common.job.JobResult;
import com.javmotion.worker.sandbox.SandboxRunner;
import com.javmotion.worker.sandbox.WorkerProperties;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JobServiceTest {

    @TempDir
    Path jobsDir;

    private WorkerProperties props(int maxConcurrent) {
        return new WorkerProperties("t", "img", maxConcurrent, jobsDir,
                new WorkerProperties.Limits("256m", "1", 64, "16m", Duration.ofSeconds(5), Duration.ofSeconds(10), 65536), Path.of("tracer.jar"));
    }

    private static JobResult ok() {
        return new JobResult(ExecutionStatus.COMPLETED, "ok", "", List.of(), 0, 1, false, null, null);
    }

    @Test
    void writesSourceUnderTheEntryClassNameAndCleansUp() {
        AtomicReference<Path> seenFile = new AtomicReference<>();
        SandboxRunner runner = (ws, className, spec) -> {
            seenFile.set(ws.src().resolve(className + ".java"));
            assertThat(seenFile.get()).exists();
            return ok();
        };
        UUID id = UUID.randomUUID();
        JobResult r = new JobService(props(1), runner).run(new JobRequest(
                id, ExecutionMode.RUN, "public class Sorter { public static void main(String[] a) {} }", null));

        assertThat(r.status()).isEqualTo(ExecutionStatus.COMPLETED);
        assertThat(seenFile.get().getFileName()).hasToString("Sorter.java");
        assertThat(Files.exists(jobsDir.resolve(id.toString()))).as("workspace deleted").isFalse();
    }

    @Test
    void cleansUpEvenWhenTheRunnerThrows() {
        SandboxRunner runner = (ws, className, spec) -> {
            throw new java.io.IOException("disk full");
        };
        UUID id = UUID.randomUUID();
        JobResult r = new JobService(props(1), runner).run(new JobRequest(id, ExecutionMode.RUN, "class A {}", null));

        assertThat(r.status()).isEqualTo(ExecutionStatus.FAILED);
        assertThat(r.errorMessage()).doesNotContain("disk full"); // internals stay in the log
        assertThat(Files.exists(jobsDir.resolve(id.toString()))).isFalse();
    }

    @Test
    void refusesWhenAllSlotsAreBusy() throws Exception {
        CountDownLatch running = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        SandboxRunner slow = (ws, className, spec) -> {
            running.countDown();
            release.await(5, TimeUnit.SECONDS);
            return ok();
        };
        JobService service = new JobService(props(1), slow);
        Thread first = Thread.ofVirtual().start(() ->
                service.run(new JobRequest(UUID.randomUUID(), ExecutionMode.RUN, "class A {}", null)));
        assertThat(running.await(5, TimeUnit.SECONDS)).isTrue();

        assertThatThrownBy(() -> service.run(new JobRequest(UUID.randomUUID(), ExecutionMode.RUN, "class A {}", null)))
                .isInstanceOf(JobService.BusyException.class);

        release.countDown();
        first.join(5000);
        assertThat(service.availableSlots()).isEqualTo(1);
    }
}
