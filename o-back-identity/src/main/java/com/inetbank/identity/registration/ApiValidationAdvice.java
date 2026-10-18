package com.inetbank.identity.registration;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;
@RestControllerAdvice
public class ApiValidationAdvice {
    @org.springframework.web.bind.annotation.ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<Map<String, String>> invalidRequest(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream().findFirst().map(e -> e.getField() + " " + e.getDefaultMessage()).orElse("Invalid registration request.");
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", message));
    }

        @org.springframework.web.bind.annotation.ExceptionHandler(
            ResponseStatusException.class
    )
    ResponseEntity<Map<String, String>> responseStatus(
            ResponseStatusException exception) {

        String message = exception.getReason() == null
                ? "Request could not be completed."
                : exception.getReason();

        return ResponseEntity.status(
                exception.getStatusCode()
        ).body(Map.of("message", message));
    }
}
