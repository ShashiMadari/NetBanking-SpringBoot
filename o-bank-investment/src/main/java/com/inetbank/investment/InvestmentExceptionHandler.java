package com.inetbank.investment;

import java.time.Instant;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Converts expected input/business-validation failures into HTTP 400.
 * Existing ResponseStatusException responses (404/409) remain unchanged.
 * Database, mail, and other unexpected failures are not disguised as HTTP 400.
 */
@RestControllerAdvice(basePackages = "com.inetbank.investment")
public class InvestmentExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiError> handleInvalidArgument(
            IllegalArgumentException exception,
            HttpServletRequest request) {

        String message = exception.getMessage();

        if (message == null || message.isBlank()) {
            message = "The request contains an invalid value.";
        }

        HttpStatus status = HttpStatus.BAD_REQUEST;

        return ResponseEntity.status(status).body(
                new ApiError(
                        Instant.now(),
                        status.value(),
                        status.getReasonPhrase(),
                        message,
                        request.getRequestURI()
                )
        );
    }

    public record ApiError(
            Instant timestamp,
            int status,
            String error,
            String message,
            String path) {
    }
}
