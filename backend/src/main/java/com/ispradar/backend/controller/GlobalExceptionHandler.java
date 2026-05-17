package com.ispradar.backend.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.NoHandlerFoundException;

import java.io.IOException;
import java.net.http.HttpTimeoutException;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(HttpTimeoutException.class)
    public ResponseEntity<Map<String, Object>> handleTimeout(HttpTimeoutException ex, HttpServletRequest request) {
        return buildError(HttpStatus.GATEWAY_TIMEOUT, ex.getMessage(), request.getRequestURI());
    }

    @ExceptionHandler(IOException.class)
    public ResponseEntity<Map<String, Object>> handleIo(IOException ex, HttpServletRequest request) {
        return buildError(HttpStatus.BAD_GATEWAY, ex.getMessage(), request.getRequestURI());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleAny(Exception ex, HttpServletRequest request) {
        return buildError(HttpStatus.INTERNAL_SERVER_ERROR, ex.getMessage(), request.getRequestURI());
    }

    @ExceptionHandler(NoHandlerFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNotFound(NoHandlerFoundException ex) {
    return buildError(HttpStatus.NOT_FOUND, "Not Found", ex.getRequestURL());
    }

    private ResponseEntity<Map<String, Object>> buildError(HttpStatus status, String message, String path) {
    return ResponseEntity.status(status)
        .body(Map.of(
            "status", status.value(),
            "error", status.getReasonPhrase(),
            "message", message == null ? "" : message,
            "path", path));
    }
}
