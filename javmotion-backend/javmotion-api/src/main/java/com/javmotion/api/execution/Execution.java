package com.javmotion.api.execution;

import com.javmotion.common.job.ExecutionMode;
import com.javmotion.common.job.ExecutionStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * One code run (RUN or VISUALIZE). Status follows the machine in backend doc §4.2; the transition
 * methods are the only way to change it.
 *
 * <p>Column definitions spell out the MySQL types from V1__init.sql so {@code ddl-auto=validate}
 * compares like with like.
 */
@Entity
@Table(name = "executions")
public class Execution {

    /** Error messages are stored in a VARCHAR(500). */
    static final int MAX_ERROR_MESSAGE = 500;

    @Id
    @Column(columnDefinition = "char(36)")
    private String id;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "program_id")
    private Long programId;

    @Column(name = "access_token_hash", columnDefinition = "char(64)")
    private String accessTokenHash;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, columnDefinition = "varchar(16)")
    private ExecutionMode mode;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, columnDefinition = "varchar(20)")
    private ExecutionStatus status;

    @Column(name = "source_code", nullable = false, columnDefinition = "mediumtext")
    private String sourceCode;

    @Column(columnDefinition = "text")
    private String stdin;

    @Column(columnDefinition = "mediumtext")
    private String stdout;

    @Column(columnDefinition = "mediumtext")
    private String stderr;

    /** JSON array of {@code {line, column, message}}. */
    @Column(name = "compile_errors", columnDefinition = "json")
    private String compileErrors;

    @Column(name = "exit_code")
    private Integer exitCode;

    @Column(name = "execution_time_ms")
    private Integer executionTimeMs;

    @Column(name = "error_message", columnDefinition = "varchar(500)")
    private String errorMessage;

    @Column(name = "created_at", nullable = false, columnDefinition = "datetime(6)")
    private Instant createdAt;

    @Column(name = "started_at", columnDefinition = "datetime(6)")
    private Instant startedAt;

    @Column(name = "finished_at", columnDefinition = "datetime(6)")
    private Instant finishedAt;

    protected Execution() {} // JPA

    public static Execution queued(
            ExecutionMode mode, String sourceCode, String stdin, String accessTokenHash, Instant now) {
        Execution e = new Execution();
        e.id = UUID.randomUUID().toString();
        e.mode = Objects.requireNonNull(mode);
        e.sourceCode = Objects.requireNonNull(sourceCode);
        e.stdin = stdin;
        e.accessTokenHash = accessTokenHash;
        e.status = ExecutionStatus.QUEUED;
        e.createdAt = now;
        return e;
    }

    // ------------------------------------------------------------------ transitions

    public void start(Instant now) {
        requireStatus(ExecutionStatus.QUEUED);
        status = ExecutionStatus.RUNNING;
        startedAt = now;
    }

    /** RUNNING → a terminal outcome (anything but QUEUED, RUNNING, CANCELLED). */
    public void finish(ExecutionResult result, Instant now) {
        requireStatus(ExecutionStatus.RUNNING);
        ExecutionStatus outcome = result.status();
        if (!outcome.isTerminal() || outcome == ExecutionStatus.CANCELLED) {
            throw new IllegalArgumentException("Not a finishing status: " + outcome);
        }
        status = outcome;
        stdout = result.stdout();
        stderr = result.stderr();
        compileErrors = result.compileErrorsJson();
        exitCode = result.exitCode();
        executionTimeMs = result.executionTimeMs();
        errorMessage = truncate(result.errorMessage());
        finishedAt = now;
    }

    /** Worker or dispatch failure: allowed from QUEUED or RUNNING. */
    public void fail(String message, Instant now) {
        if (status.isTerminal()) throw illegal(ExecutionStatus.FAILED);
        status = ExecutionStatus.FAILED;
        errorMessage = truncate(message);
        finishedAt = now;
    }

    public void cancel(Instant now) {
        if (status.isTerminal()) throw illegal(ExecutionStatus.CANCELLED);
        status = ExecutionStatus.CANCELLED;
        finishedAt = now;
    }

    private void requireStatus(ExecutionStatus expected) {
        if (status != expected) {
            throw new IllegalStateException("Execution " + id + " is " + status + ", expected " + expected);
        }
    }

    private IllegalStateException illegal(ExecutionStatus target) {
        return new IllegalStateException("Execution " + id + " cannot move from " + status + " to " + target);
    }

    private static String truncate(String message) {
        if (message == null || message.length() <= MAX_ERROR_MESSAGE) return message;
        return message.substring(0, MAX_ERROR_MESSAGE - 1) + "…";
    }

    // ------------------------------------------------------------------ getters

    public String getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public Long getProgramId() {
        return programId;
    }

    public String getAccessTokenHash() {
        return accessTokenHash;
    }

    public ExecutionMode getMode() {
        return mode;
    }

    public ExecutionStatus getStatus() {
        return status;
    }

    public String getSourceCode() {
        return sourceCode;
    }

    public String getStdin() {
        return stdin;
    }

    public String getStdout() {
        return stdout;
    }

    public String getStderr() {
        return stderr;
    }

    public String getCompileErrors() {
        return compileErrors;
    }

    public Integer getExitCode() {
        return exitCode;
    }

    public Integer getExecutionTimeMs() {
        return executionTimeMs;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getFinishedAt() {
        return finishedAt;
    }
}
