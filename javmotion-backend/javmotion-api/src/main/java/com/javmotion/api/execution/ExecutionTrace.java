package com.javmotion.api.execution;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** Gzipped trace JSON for a VISUALIZE execution — one row, not one row per step (doc §9.2). */
@Entity
@Table(name = "execution_traces")
public class ExecutionTrace {

    @Id
    @Column(name = "execution_id", columnDefinition = "char(36)")
    private String executionId;

    @Column(name = "schema_version", nullable = false)
    private int schemaVersion;

    @Column(name = "step_count", nullable = false)
    private int stepCount;

    @Column(nullable = false)
    private boolean truncated;

    @Column(name = "trace_gzip", nullable = false, columnDefinition = "longblob")
    private byte[] traceGzip;

    @Column(name = "created_at", nullable = false, columnDefinition = "datetime(6)")
    private Instant createdAt;

    protected ExecutionTrace() {} // JPA

    public ExecutionTrace(
            String executionId, int schemaVersion, int stepCount, boolean truncated, byte[] traceGzip, Instant createdAt) {
        this.executionId = executionId;
        this.schemaVersion = schemaVersion;
        this.stepCount = stepCount;
        this.truncated = truncated;
        this.traceGzip = traceGzip;
        this.createdAt = createdAt;
    }

    public String getExecutionId() {
        return executionId;
    }

    public int getSchemaVersion() {
        return schemaVersion;
    }

    public int getStepCount() {
        return stepCount;
    }

    public boolean isTruncated() {
        return truncated;
    }

    public byte[] getTraceGzip() {
        return traceGzip;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
