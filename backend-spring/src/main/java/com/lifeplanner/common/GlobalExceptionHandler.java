package com.lifeplanner.common;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.AuthenticationException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log =
            LoggerFactory.getLogger(
                    GlobalExceptionHandler.class);

    private ResponseEntity<Map<String, Object>> body(
            HttpStatus status,
            String code,
            String message) {

        Map<String, Object> payload =
                new LinkedHashMap<>();

        payload.put(
                "timestamp",
                Instant.now().toString());

        payload.put("error", code);
        payload.put("message", message);

        return ResponseEntity
                .status(status)
                .body(payload);
    }

    @ExceptionHandler(
            ApiExceptions.NotFoundException.class)
    public ResponseEntity<Map<String, Object>> notFound(
            ApiExceptions.NotFoundException ex) {

        return body(
                HttpStatus.NOT_FOUND,
                "NOT_FOUND",
                ex.getMessage());
    }

    @ExceptionHandler(
            ApiExceptions.ConflictException.class)
    public ResponseEntity<Map<String, Object>> conflict(
            ApiExceptions.ConflictException ex) {

        return body(
                HttpStatus.CONFLICT,
                "CONFLICT",
                ex.getMessage());
    }

    @ExceptionHandler(
            ApiExceptions.PlannerUnavailableException.class)
    public ResponseEntity<Map<String, Object>> planner(
            ApiExceptions.PlannerUnavailableException ex) {

        log.error(
                "Planner service unavailable",
                ex);

        return body(
                HttpStatus.SERVICE_UNAVAILABLE,
                "PLANNER_UNAVAILABLE",
                "The planning service is not reachable right now, "
                        + "so no schedule was generated. "
                        + "Nothing in your plan has been changed.");
    }

    @ExceptionHandler(
            MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> validation(
            MethodArgumentNotValidException ex) {

        String detail =
                ex.getBindingResult()
                        .getFieldErrors()
                        .stream()
                        .map(error ->
                                error.getField()
                                        + ": "
                                        + error.getDefaultMessage())
                        .collect(Collectors.joining("; "));

        return body(
                HttpStatus.BAD_REQUEST,
                "VALIDATION_FAILED",
                detail);
    }

    @ExceptionHandler(
            HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> malformedRequest(
            HttpMessageNotReadableException ex) {

        return body(
                HttpStatus.BAD_REQUEST,
                "INVALID_REQUEST",
                "The request body is missing or has an invalid format.");
    }

    @ExceptionHandler(
            IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> illegalArgument(
            IllegalArgumentException ex) {

        return body(
                HttpStatus.BAD_REQUEST,
                "INVALID_REQUEST",
                ex.getMessage());
    }

    @ExceptionHandler(
            AuthenticationException.class)
    public ResponseEntity<Map<String, Object>> auth(
            AuthenticationException ex) {

        return body(
                HttpStatus.UNAUTHORIZED,
                "AUTHENTICATION_FAILED",
                "Email or password is incorrect.");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> unexpected(
            Exception ex) {

        log.error(
                "Unhandled error",
                ex);

        return body(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "INTERNAL_ERROR",
                "Something went wrong on our side. "
                        + "Please try again.");
    }
}