package com.javmotion.api.execution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.javmotion.common.job.ExecutionMode;
import com.javmotion.common.job.ExecutionStatus;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/** Status machine from backend doc §4.2 — no database involved. */
class ExecutionTest {

    private static final Instant T0 = Instant.parse("2026-11-21T10:00:00Z");

    private static Execution queued() {
        return Execution.queued(ExecutionMode.RUN, "class Main {}", null, "hash", T0);
    }

    private static ExecutionResult result(ExecutionStatus status) {
        return new ExecutionResult(status, "out", "", null, 0, 42, null);
    }

    @Test
    void startsQueuedWithUuid() {
        Execution e = queued();
        assertThat(e.getStatus()).isEqualTo(ExecutionStatus.QUEUED);
        assertThat(e.getId()).hasSize(36);
        assertThat(e.getCreatedAt()).isEqualTo(T0);
    }

    @Test
    void runsToCompletion() {
        Execution e = queued();
        e.start(T0.plusMillis(10));
        e.finish(result(ExecutionStatus.COMPLETED), T0.plusMillis(60));

        assertThat(e.getStatus()).isEqualTo(ExecutionStatus.COMPLETED);
        assertThat(e.getStartedAt()).isEqualTo(T0.plusMillis(10));
        assertThat(e.getFinishedAt()).isEqualTo(T0.plusMillis(60));
        assertThat(e.getStdout()).isEqualTo("out");
        assertThat(e.getExecutionTimeMs()).isEqualTo(42);
    }

    @ParameterizedTest
    @EnumSource(value = ExecutionStatus.class, names = {"COMPLETED", "COMPILE_ERROR", "RUNTIME_ERROR", "TIMEOUT", "LIMIT_EXCEEDED", "FAILED"})
    void finishAcceptsEveryOutcome(ExecutionStatus outcome) {
        Execution e = queued();
        e.start(T0);
        e.finish(result(outcome), T0);
        assertThat(e.getStatus()).isEqualTo(outcome);
    }

    @ParameterizedTest
    @EnumSource(value = ExecutionStatus.class, names = {"QUEUED", "RUNNING", "CANCELLED"})
    void finishRejectsNonOutcomes(ExecutionStatus notAnOutcome) {
        Execution e = queued();
        e.start(T0);
        assertThatThrownBy(() -> e.finish(result(notAnOutcome), T0)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void cannotFinishWithoutStarting() {
        assertThatThrownBy(() -> queued().finish(result(ExecutionStatus.COMPLETED), T0))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("expected RUNNING");
    }

    @Test
    void cannotStartTwice() {
        Execution e = queued();
        e.start(T0);
        assertThatThrownBy(() -> e.start(T0)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void cancelFromQueuedOrRunningOnly() {
        Execution queued = queued();
        queued.cancel(T0);
        assertThat(queued.getStatus()).isEqualTo(ExecutionStatus.CANCELLED);

        Execution running = queued();
        running.start(T0);
        running.cancel(T0);
        assertThat(running.getStatus()).isEqualTo(ExecutionStatus.CANCELLED);

        Execution done = queued();
        done.start(T0);
        done.finish(result(ExecutionStatus.COMPLETED), T0);
        assertThatThrownBy(() -> done.cancel(T0)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void failTruncatesLongMessages() {
        Execution e = queued();
        e.fail("x".repeat(2_000), T0);
        assertThat(e.getStatus()).isEqualTo(ExecutionStatus.FAILED);
        assertThat(e.getErrorMessage()).hasSize(Execution.MAX_ERROR_MESSAGE);
    }

    @Test
    void cannotFailAfterCancel() {
        Execution e = queued();
        e.cancel(T0);
        assertThatThrownBy(() -> e.fail("late", T0)).isInstanceOf(IllegalStateException.class);
    }
}
