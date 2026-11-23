package com.javmotion.api.execution;

import com.javmotion.api.common.error.ApiException;
import com.javmotion.api.config.ExecutionProperties;
import com.javmotion.common.job.ExecutionMode;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Creates executions and guards access to them. Dispatching is {@link ExecutionDispatcher}'s job. */
@Service
public class ExecutionService {

    /** A created execution plus the raw access token (returned to the client exactly once). */
    public record Created(Execution execution, String accessToken) {}

    private final ExecutionRepository executions;
    private final ExecutionTraceRepository traces;
    private final ExecutionProperties props;
    private final Clock clock;

    public ExecutionService(
            ExecutionRepository executions, ExecutionTraceRepository traces, ExecutionProperties props, Clock clock) {
        this.executions = executions;
        this.traces = traces;
        this.props = props;
        this.clock = clock;
    }

    @Transactional
    public Created create(String sourceCode, ExecutionMode mode, String stdin) {
        if (sourceCode.getBytes(StandardCharsets.UTF_8).length > props.maxSourceBytes()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "SOURCE_TOO_LARGE",
                    "Source exceeds " + props.maxSourceBytes() / 1024 + " KB.");
        }
        if (stdin != null && stdin.getBytes(StandardCharsets.UTF_8).length > props.maxStdinBytes()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "STDIN_TOO_LARGE",
                    "Input exceeds " + props.maxStdinBytes() / 1024 + " KB.");
        }
        String token = AccessTokens.generate();
        Execution e = Execution.queued(mode, sourceCode, stdin, AccessTokens.hash(token), clock.instant());
        return new Created(executions.save(e), token);
    }

    /**
     * Same 404 for "no such id" and "wrong token", so ids cannot be probed.
     * TODO (Step B7): owners (logged-in users) get access without a token.
     */
    @Transactional(readOnly = true)
    public Execution get(String id, String token) {
        return executions.findById(id)
                .filter(e -> AccessTokens.matches(token, e.getAccessTokenHash()))
                .orElseThrow(() -> ApiException.notFound("Execution"));
    }

    @Transactional(readOnly = true)
    public Optional<ExecutionTrace> trace(String id, String token) {
        get(id, token);
        return traces.findById(id);
    }

    /** Cancels a queued/running execution; deletes a finished one (doc §7.1). */
    @Transactional
    public void cancelOrDelete(String id, String token) {
        Execution e = get(id, token);
        if (e.getStatus().isTerminal()) {
            executions.delete(e);
        } else {
            e.cancel(clock.instant());
            executions.save(e);
        }
    }
}
