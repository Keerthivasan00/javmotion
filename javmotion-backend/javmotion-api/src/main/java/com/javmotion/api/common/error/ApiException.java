package com.javmotion.api.common.error;

import org.springframework.http.HttpStatus;

/**
 * Throw from services for expected failures. Rendered as a ProblemDetail with a stable {@code code}
 * the frontend can switch on, e.g. {@code new ApiException(HttpStatus.BAD_REQUEST, "SOURCE_TOO_LARGE", "...")}.
 */
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    public ApiException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }

    public static ApiException notFound(String what) {
        return new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", what + " not found");
    }
}
