-- Phase 3: code execution. Backend doc §9.1.
-- user_id / program_id get their foreign keys when `users` and `programs` arrive (Step B7).

CREATE TABLE executions (
    id                CHAR(36)     NOT NULL,
    user_id           BIGINT       NULL,
    program_id        BIGINT       NULL,
    access_token_hash CHAR(64)     NULL,
    mode              VARCHAR(16)  NOT NULL,
    status            VARCHAR(20)  NOT NULL,
    source_code       MEDIUMTEXT   NOT NULL,
    stdin             TEXT         NULL,
    stdout            MEDIUMTEXT   NULL,
    stderr            MEDIUMTEXT   NULL,
    compile_errors    JSON         NULL,
    exit_code         INT          NULL,
    execution_time_ms INT          NULL,
    error_message     VARCHAR(500) NULL,
    created_at        DATETIME(6)  NOT NULL,
    started_at        DATETIME(6)  NULL,
    finished_at       DATETIME(6)  NULL,
    PRIMARY KEY (id),
    INDEX idx_executions_user_created (user_id, created_at),
    INDEX idx_executions_status_created (status, created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- One gzipped JSON blob per execution instead of one row per step (doc §9.2).
CREATE TABLE execution_traces (
    execution_id   CHAR(36)    NOT NULL,
    schema_version INT         NOT NULL,
    step_count     INT         NOT NULL,
    truncated      BOOLEAN     NOT NULL,
    trace_gzip     LONGBLOB    NOT NULL,
    created_at     DATETIME(6) NOT NULL,
    PRIMARY KEY (execution_id),
    CONSTRAINT fk_execution_traces_execution
        FOREIGN KEY (execution_id) REFERENCES executions (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
