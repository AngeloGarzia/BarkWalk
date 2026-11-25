package com.barkwalk.web;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

// Traduit les erreurs métier et de validation en réponses HTTP homogènes.
@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail handleValidation(MethodArgumentNotValidException ex) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        problem.setTitle("Validation failed");
        problem.setProperty("timestamp", Instant.now());
        Map<String, String> errors = new LinkedHashMap<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            errors.put(error.getField(), error.getDefaultMessage());
        }
        problem.setProperty("errors", errors);
        return problem;
    }

    @ExceptionHandler(ResponseStatusException.class)
    ProblemDetail handleResponseStatus(ResponseStatusException ex) {
        ProblemDetail problem = ProblemDetail.forStatus(ex.getStatusCode());
        problem.setTitle(ex.getReason() != null ? ex.getReason() : ex.getMessage());
        problem.setProperty("timestamp", Instant.now());
        return problem;
    }

    @ExceptionHandler(BadCredentialsException.class)
    ProblemDetail handleBadCredentials(BadCredentialsException ex) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.UNAUTHORIZED);
        problem.setTitle(ex.getMessage());
        problem.setProperty("timestamp", Instant.now());
        return problem;
    }

    @ExceptionHandler(AccessDeniedException.class)
    ProblemDetail handleAccessDenied(AccessDeniedException ex) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.FORBIDDEN);
        problem.setTitle("Access denied");
        problem.setProperty("timestamp", Instant.now());
        return problem;
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ProblemDetail handleIllegalArgument(IllegalArgumentException ex) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        problem.setTitle(ex.getMessage());
        problem.setProperty("timestamp", Instant.now());
        return problem;
    }

    @ExceptionHandler(UnsupportedOperationException.class)
    ProblemDetail handleNotImplemented(UnsupportedOperationException ex) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.NOT_IMPLEMENTED);
        problem.setTitle(ex.getMessage());
        problem.setProperty("timestamp", Instant.now());
        return problem;
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail handleGeneric(Exception ex) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.INTERNAL_SERVER_ERROR);
        problem.setTitle("Internal server error");
        problem.setProperty("timestamp", Instant.now());
        return problem;
    }
}
