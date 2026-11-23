package com.javmotion.api.execution;

import com.javmotion.api.config.WorkerClientProperties;
import com.javmotion.common.job.JobRequest;
import com.javmotion.common.job.JobResult;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/** Calls {@code POST /internal/jobs} on the worker with the shared secret. */
@Component
public class WorkerClient {

    static final String TOKEN_HEADER = "X-Worker-Token";

    /** Worker refused: every sandbox slot is busy (HTTP 429). Retry later. */
    public static class WorkerBusyException extends RuntimeException {
        WorkerBusyException() {
            super("Worker busy");
        }
    }

    /** Worker unreachable or answered with an unexpected error. */
    public static class WorkerUnavailableException extends RuntimeException {
        WorkerUnavailableException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    private final RestClient http;

    public WorkerClient(WorkerClientProperties props) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5_000);
        factory.setReadTimeout((int) props.timeout().toMillis());
        this.http = RestClient.builder()
                .baseUrl(props.baseUrl())
                .defaultHeader(TOKEN_HEADER, props.token())
                .requestFactory(factory)
                .build();
    }

    public JobResult run(JobRequest request) {
        try {
            JobResult result = http.post().uri("/internal/jobs").body(request).retrieve().body(JobResult.class);
            if (result == null) throw new WorkerUnavailableException("Empty worker response", null);
            return result;
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode().isSameCodeAs(HttpStatus.TOO_MANY_REQUESTS)) throw new WorkerBusyException();
            throw new WorkerUnavailableException("Worker rejected job: " + e.getStatusCode(), e);
        } catch (RestClientException e) {
            throw new WorkerUnavailableException("Worker unreachable: " + e.getMessage(), e);
        }
    }
}
