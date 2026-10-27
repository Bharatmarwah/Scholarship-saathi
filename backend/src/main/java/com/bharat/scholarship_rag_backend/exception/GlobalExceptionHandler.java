package com.bharat.scholarship_rag_backend.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;
import java.util.stream.Collectors;

/**
 * Converts unexpected failures into a stable JSON shape instead of an empty
 * response or a stack trace. The detail is logged server-side; the client gets
 * a short, actionable message without internal state.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> validationFailure(
            MethodArgumentNotValidException exception) {

        String detail = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> {
                    String message = error.getDefaultMessage();
                    if (message == null || message.isBlank()) {
                        message = "is invalid";
                    }
                    return message.startsWith(error.getField())
                            ? message
                            : error.getField() + " " + message;
                })
                .distinct()
                .collect(Collectors.joining("; "));

        return ResponseEntity.badRequest().body(Map.of(
                "error", "invalid_request",
                "message", detail.isBlank() ? "The request is not valid." : detail));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, String>> unreadableBody(
            HttpMessageNotReadableException exception) {
        log.warn("Rejected a request with an unreadable body: {}", exception.getMessage());
        return ResponseEntity.badRequest().body(Map.of(
                "error", "invalid_request",
                "message", "The request body is not valid JSON."));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> unexpectedFailure(Exception exception) {
        log.error("Unhandled error while processing a request", exception);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                "error", "internal_error",
                "message", "Something went wrong while processing the request. "
                        + "Please try again."));
    }
}
