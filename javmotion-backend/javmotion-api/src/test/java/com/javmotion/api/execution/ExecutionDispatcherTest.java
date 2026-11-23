package com.javmotion.api.execution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.javmotion.common.job.CompileError;
import com.javmotion.common.job.ExecutionMode;
import com.javmotion.common.job.ExecutionStatus;
import com.javmotion.common.job.JobResult;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.zip.GZIPInputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.json.JsonMapper;

class ExecutionDispatcherTest {

    private final ExecutionRepository executions = mock(ExecutionRepository.class);
    private final ExecutionTraceRepository traces = mock(ExecutionTraceRepository.class);
    private final WorkerClient worker = mock(WorkerClient.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-23T10:00:00Z"), ZoneOffset.UTC);
    private final ExecutionDispatcher dispatcher = new ExecutionDispatcher(
            executions, traces, worker, JsonMapper.builder().build(), clock, Duration.ZERO);

    private Execution execution;

    @BeforeEach
    void setUp() {
        execution = Execution.queued(ExecutionMode.RUN, "class Main {}", null, "h".repeat(64), clock.instant());
        given(executions.findById(execution.getId())).willReturn(Optional.of(execution));
    }

    private static JobResult result(ExecutionStatus status) {
        return new JobResult(status, "out\n", "", List.of(), 0, 15, false, null, null);
    }

    @Test
    void runsAndStoresTheResult() {
        given(worker.run(any())).willReturn(result(ExecutionStatus.COMPLETED));

        dispatcher.dispatch(execution.getId());

        assertThat(execution.getStatus()).isEqualTo(ExecutionStatus.COMPLETED);
        assertThat(execution.getStdout()).isEqualTo("out\n");
        assertThat(execution.getExecutionTimeMs()).isEqualTo(15);
        assertThat(execution.getStartedAt()).isNotNull();
        verify(executions, times(2)).save(execution); // RUNNING, then COMPLETED
    }

    @Test
    void storesCompileErrorsAsJson() {
        given(worker.run(any())).willReturn(new JobResult(ExecutionStatus.COMPILE_ERROR, "", "Main.java:1: error",
                List.of(new CompileError(1, 5, "';' expected")), null, 3, false, null, null));

        dispatcher.dispatch(execution.getId());

        assertThat(execution.getStatus()).isEqualTo(ExecutionStatus.COMPILE_ERROR);
        assertThat(execution.getCompileErrors()).isEqualTo("[{\"line\":1,\"column\":5,\"message\":\"';' expected\"}]");
    }

    @Test
    void retriesWhenTheWorkerIsBusy() {
        given(worker.run(any()))
                .willThrow(new WorkerClient.WorkerBusyException())
                .willReturn(result(ExecutionStatus.COMPLETED));

        dispatcher.dispatch(execution.getId());

        verify(worker, times(2)).run(any());
        assertThat(execution.getStatus()).isEqualTo(ExecutionStatus.COMPLETED);
    }

    @Test
    void givesUpAfterRepeatedBusyAnswers() {
        given(worker.run(any())).willThrow(new WorkerClient.WorkerBusyException());

        dispatcher.dispatch(execution.getId());

        verify(worker, times(ExecutionDispatcher.MAX_ATTEMPTS)).run(any());
        assertThat(execution.getStatus()).isEqualTo(ExecutionStatus.FAILED);
        assertThat(execution.getErrorMessage()).contains("busy");
    }

    @Test
    void workerDownMarksFailedWithoutLeakingInternals() {
        given(worker.run(any())).willThrow(new WorkerClient.WorkerUnavailableException("Connection refused: 10.0.0.5", null));

        dispatcher.dispatch(execution.getId());

        assertThat(execution.getStatus()).isEqualTo(ExecutionStatus.FAILED);
        assertThat(execution.getErrorMessage()).doesNotContain("10.0.0.5");
    }

    @Test
    void keepsCancellationThatHappenedDuringTheRun() {
        given(worker.run(any())).willAnswer(inv -> {
            execution.cancel(clock.instant()); // user pressed stop while the worker was busy
            return result(ExecutionStatus.COMPLETED);
        });

        dispatcher.dispatch(execution.getId());

        assertThat(execution.getStatus()).isEqualTo(ExecutionStatus.CANCELLED);
        assertThat(execution.getStdout()).isNull();
    }

    @Test
    void skipsExecutionsThatAreNoLongerQueued() {
        execution.cancel(clock.instant());
        dispatcher.dispatch(execution.getId());
        verify(worker, never()).run(any());
    }

    @Test
    void storesVisualizeTraceGzipped() throws Exception {
        String trace = """
                {"schemaVersion":1,"source":"JDI","language":"java","truncated":false,"steps":[
                {"index":0,"event":"END","line":1,"stack":[],"heap":{},"stdout":""}]}""";
        given(worker.run(any())).willReturn(
                new JobResult(ExecutionStatus.COMPLETED, "", "", List.of(), 0, 9, false, null, trace));

        dispatcher.dispatch(execution.getId());

        ArgumentCaptor<ExecutionTrace> saved = ArgumentCaptor.forClass(ExecutionTrace.class);
        verify(traces).save(saved.capture());
        assertThat(saved.getValue().getStepCount()).isEqualTo(1);
        try (var in = new GZIPInputStream(new ByteArrayInputStream(saved.getValue().getTraceGzip()))) {
            assertThat(new String(in.readAllBytes(), StandardCharsets.UTF_8)).isEqualTo(trace);
        }
    }
}
