package com.javmotion.api.execution;

import static org.assertj.core.api.Assertions.assertThat;

import com.javmotion.common.job.ExecutionMode;
import com.javmotion.common.job.ExecutionStatus;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

/**
 * Persistence checks shared by the Testcontainers and local-MySQL variants. Starting the
 * context at all proves Flyway ran V1 and Hibernate's {@code ddl-auto=validate} accepted it.
 *
 * <p>Explicitly {@code @Transactional} so every test rolls back: in Spring Boot 4,
 * {@code @DataJpaTest} alone did not, and rows were committed to the local database.
 */
@Transactional
abstract class ExecutionPersistenceContract {

    @Autowired
    ExecutionRepository executions;

    @Autowired
    ExecutionTraceRepository traces;

    @Autowired
    EntityManager em;

    // MySQL DATETIME(6) keeps microseconds; trim so round-trip comparisons are exact.
    private static final Instant NOW = Instant.now().truncatedTo(ChronoUnit.MICROS);

    @Test
    void savesAndReloadsAFinishedExecution() {
        Execution e = Execution.queued(ExecutionMode.VISUALIZE, "public class Main {}", "in", "a".repeat(64), NOW);
        e.start(NOW.plusMillis(5));
        e.finish(new ExecutionResult(
                        ExecutionStatus.COMPILE_ERROR,
                        "",
                        "Main.java:1: error",
                        "[{\"line\":1,\"column\":5,\"message\":\"';' expected\"}]",
                        1,
                        120,
                        null),
                NOW.plusMillis(125));
        executions.saveAndFlush(e);
        em.clear();

        Execution loaded = executions.findById(e.getId()).orElseThrow();
        assertThat(loaded.getStatus()).isEqualTo(ExecutionStatus.COMPILE_ERROR);
        assertThat(loaded.getMode()).isEqualTo(ExecutionMode.VISUALIZE);
        assertThat(loaded.getCreatedAt()).isEqualTo(NOW);
        assertThat(loaded.getFinishedAt()).isEqualTo(NOW.plusMillis(125));
        assertThat(loaded.getCompileErrors()).contains("\"line\"");
        assertThat(loaded.getAccessTokenHash()).hasSize(64);
    }

    @Test
    void storesGzippedTraceAndCascadesDelete() {
        Execution e = Execution.queued(ExecutionMode.VISUALIZE, "class A {}", null, null, NOW);
        executions.saveAndFlush(e);
        byte[] blob = new byte[200_000]; // larger than BLOB (64 KB) to prove LONGBLOB
        blob[199_999] = 7;
        traces.saveAndFlush(new ExecutionTrace(e.getId(), 1, 1234, true, blob, NOW));
        em.clear();

        ExecutionTrace loaded = traces.findById(e.getId()).orElseThrow();
        assertThat(loaded.getTraceGzip()).hasSize(200_000);
        assertThat(loaded.getTraceGzip()[199_999]).isEqualTo((byte) 7);
        assertThat(loaded.isTruncated()).isTrue();

        // ON DELETE CASCADE in V1: deleting the execution row removes its trace.
        em.createNativeQuery("DELETE FROM executions WHERE id = ?1").setParameter(1, e.getId()).executeUpdate();
        em.clear();
        assertThat(traces.findById(e.getId())).isEmpty();
    }
}
