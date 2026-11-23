package com.javmotion.worker.api;

import com.javmotion.common.job.JobRequest;
import com.javmotion.common.job.JobResult;
import com.javmotion.worker.job.JobService;
import com.javmotion.worker.sandbox.WorkerProperties;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Only the API calls this (shared secret + private network). Synchronous: the response is the
 * finished result; the API calls it from a background thread.
 */
@RestController
@RequestMapping("/internal")
public class JobController {

    public static final String TOKEN_HEADER = "X-Worker-Token";

    private final JobService jobs;
    private final byte[] expectedToken;

    public JobController(JobService jobs, WorkerProperties props) {
        this.jobs = jobs;
        this.expectedToken = props.token().getBytes(StandardCharsets.UTF_8);
    }

    @PostMapping("/jobs")
    public ResponseEntity<JobResult> run(
            @RequestHeader(name = TOKEN_HEADER, required = false) String token, @RequestBody JobRequest request) {
        if (token == null || !MessageDigest.isEqual(expectedToken, token.getBytes(StandardCharsets.UTF_8))) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        if (request.jobId() == null || request.mode() == null || request.sourceCode() == null) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(jobs.run(request));
    }

    @ExceptionHandler(JobService.BusyException.class)
    ResponseEntity<Void> busy() {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).build();
    }
}
