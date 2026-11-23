package com.javmotion.api.execution;

import com.javmotion.common.job.CompileError;
import com.javmotion.common.job.ExecutionMode;
import com.javmotion.common.job.ExecutionStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.zip.GZIPInputStream;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * Run code (doc §7.1). Submit returns 202 at once; the client polls {@code GET /{id}} until the
 * status is terminal, then reads {@code /output} (and {@code /events} for VISUALIZE).
 * Anonymous callers prove access with the {@code X-Execution-Token} returned on submit.
 */
@RestController
@RequestMapping("/api/v1/executions")
public class ExecutionController {

    public static final String TOKEN_HEADER = "X-Execution-Token";

    private final ExecutionService service;
    private final ExecutionDispatcher dispatcher;
    private final JsonMapper json;

    public ExecutionController(ExecutionService service, ExecutionDispatcher dispatcher, JsonMapper json) {
        this.service = service;
        this.dispatcher = dispatcher;
        this.json = json;
    }

    public record CreateRequest(
            @NotBlank @Size(max = 65_536) String sourceCode,
            @Pattern(regexp = "java", message = "only java is supported") String language,
            @NotNull ExecutionMode mode,
            @Size(max = 8_192) String stdin) {}

    public record CreateResponse(String id, String accessToken, ExecutionStatus status) {}

    public record StatusResponse(
            String id,
            ExecutionMode mode,
            ExecutionStatus status,
            Integer exitCode,
            Integer executionTimeMs,
            String errorMessage,
            Instant createdAt,
            Instant startedAt,
            Instant finishedAt) {}

    public record OutputResponse(
            String id, ExecutionStatus status, String stdout, String stderr, List<CompileError> compileErrors) {}

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public CreateResponse create(@Valid @RequestBody CreateRequest request) {
        ExecutionService.Created created = service.create(request.sourceCode(), request.mode(), request.stdin());
        // create() has committed, so the background thread will find the row.
        dispatcher.dispatch(created.execution().getId());
        return new CreateResponse(created.execution().getId(), created.accessToken(), created.execution().getStatus());
    }

    @GetMapping("/{id}")
    public StatusResponse status(@PathVariable String id, @RequestHeader(name = TOKEN_HEADER, required = false) String token) {
        Execution e = service.get(id, token);
        return new StatusResponse(e.getId(), e.getMode(), e.getStatus(), e.getExitCode(), e.getExecutionTimeMs(),
                e.getErrorMessage(), e.getCreatedAt(), e.getStartedAt(), e.getFinishedAt());
    }

    @GetMapping("/{id}/output")
    public OutputResponse output(@PathVariable String id, @RequestHeader(name = TOKEN_HEADER, required = false) String token) {
        Execution e = service.get(id, token);
        List<CompileError> errors = e.getCompileErrors() == null
                ? List.of()
                : json.readValue(e.getCompileErrors(), new TypeReference<List<CompileError>>() {});
        return new OutputResponse(e.getId(), e.getStatus(), e.getStdout(), e.getStderr(), errors);
    }

    /** Trace JSON (doc §6). 404 until a VISUALIZE run has stored one. */
    @GetMapping(value = "/{id}/events", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> events(@PathVariable String id, @RequestHeader(name = TOKEN_HEADER, required = false) String token) {
        return service.trace(id, token)
                .map(t -> ResponseEntity.ok(gunzip(t.getTraceGzip())))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String id, @RequestHeader(name = TOKEN_HEADER, required = false) String token) {
        service.cancelOrDelete(id, token);
    }

    private static String gunzip(byte[] gz) {
        try (GZIPInputStream in = new GZIPInputStream(new ByteArrayInputStream(gz))) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
