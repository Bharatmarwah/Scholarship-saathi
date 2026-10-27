package com.bharat.scholarship_rag_backend.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void malformedJsonIsABadRequestNotAServerError() {
        ResponseEntity<Map<String, String>> response = handler.unreadableBody(
                new HttpMessageNotReadableException("bad json", null));

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("invalid_request", response.getBody().get("error"));
    }

    @Test
    void fieldMessagesAreNeitherDuplicatedNorOpaque() {
        MethodArgumentNotValidException exception = mock(MethodArgumentNotValidException.class);
        BindingResult bindingResult = mock(BindingResult.class);
        when(exception.getBindingResult()).thenReturn(bindingResult);
        when(bindingResult.getFieldErrors()).thenReturn(List.of(
                new FieldError("chatRequest", "conversationId", "must not be blank"),
                new FieldError("chatRequest", "conversationId",
                        "conversationId must not contain whitespace")));

        ResponseEntity<Map<String, String>> response = handler.validationFailure(exception);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("invalid_request", response.getBody().get("error"));
        assertEquals("conversationId must not be blank; "
                        + "conversationId must not contain whitespace",
                response.getBody().get("message"));
    }

    @Test
    void unexpectedFailureDoesNotLeakTheExceptionMessage() {
        ResponseEntity<Map<String, String>> response = handler.unexpectedFailure(
                new IllegalStateException("database password was hunter2"));

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertFalse(response.getBody().get("message").contains("hunter2"));
    }
}
