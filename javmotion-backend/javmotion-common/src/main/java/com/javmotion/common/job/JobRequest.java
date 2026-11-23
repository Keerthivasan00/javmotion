package com.javmotion.common.job;

import java.util.UUID;

/** API → worker: run one program ({@code POST /internal/jobs}). */
public record JobRequest(UUID jobId, ExecutionMode mode, String sourceCode, String stdin) {}
