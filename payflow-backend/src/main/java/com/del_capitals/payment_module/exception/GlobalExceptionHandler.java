package com.del_capitals.payment_module.exception;



import com.del_capitals.payment_module.dto.response.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.exception.LockAcquisitionException;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    // ─── Payment Domain Exceptions ────────────────────────────────────────────
    @ExceptionHandler(PaymentException.class)
    public ResponseEntity<ErrorResponse> handlePaymentException(
            PaymentException ex, HttpServletRequest request) {
        log.warn("Payment exception [{}]: {} | Path: {}",
                ex.getErrorCode(), ex.getMessage(), request.getRequestURI());

        return ResponseEntity
                .status(ex.getHttpStatus())
                .body(buildError(ex.getErrorCode(), ex.getMessage(), request));
    }

    // ─── Optimistic Locking Conflict ──────────────────────────────────────────
    @ExceptionHandler({
            OptimisticLockingFailureException.class,
            ObjectOptimisticLockingFailureException.class
    })
    public ResponseEntity<ErrorResponse> handleOptimisticLocking(
            Exception ex, HttpServletRequest request) {
        log.warn("Optimistic locking conflict at {}: {}", request.getRequestURI(), ex.getMessage());

        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(buildError(
                        "CONCURRENT_MODIFICATION",
                        "The resource was modified by another request. Please retry your operation.",
                        request
                ));
    }

    // ─── Pessimistic Locking Timeout ──────────────────────────────────────────
    @ExceptionHandler({
            CannotAcquireLockException.class,
            LockAcquisitionException.class
    })
    public ResponseEntity<ErrorResponse> handleLockAcquisition(
            Exception ex, HttpServletRequest request) {
        log.warn("Lock acquisition failed at {}: {}", request.getRequestURI(), ex.getMessage());

        return ResponseEntity
                .status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(buildError(
                        "LOCK_ACQUISITION_FAILED",
                        "System is busy processing another request. Please retry in a moment.",
                        request
                ));
    }

    // ─── Data Integrity (DB Constraint Violations) ───────────────────────────
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrity(
            DataIntegrityViolationException ex, HttpServletRequest request) {
        log.error("Data integrity violation at {}: {}", request.getRequestURI(), ex.getMessage());

        String message = "Data integrity error";
        String code = "DATA_INTEGRITY_ERROR";

        if (ex.getMessage() != null) {
            if (ex.getMessage().contains("idempotency_key")) {
                message = "Duplicate request detected. Transaction already submitted.";
                code = "DUPLICATE_IDEMPOTENCY_KEY";
            } else if (ex.getMessage().contains("reference_id")) {
                message = "Duplicate reference ID. Transaction already exists.";
                code = "DUPLICATE_REFERENCE";
            }
        }

        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(buildError(code, message, request));
    }

    // ─── Bean Validation Errors ───────────────────────────────────────────────
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(
            MethodArgumentNotValidException ex, HttpServletRequest request) {
        Map<String, String> fieldErrors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach(error -> {
            String field = error instanceof FieldError ? ((FieldError) error).getField() : error.getObjectName();
            fieldErrors.put(field, error.getDefaultMessage());
        });

        log.warn("Validation failed at {}: {}", request.getRequestURI(), fieldErrors);

        ErrorResponse response = buildError("VALIDATION_ERROR", "Request validation failed", request);
        response.setFieldErrors(fieldErrors);

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    // ─── Constraint Violation ─────────────────────────────────────────────────
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolation(
            ConstraintViolationException ex, HttpServletRequest request) {
        Map<String, String> violations = new HashMap<>();
        ex.getConstraintViolations().forEach(cv ->
                violations.put(cv.getPropertyPath().toString(), cv.getMessage())
        );

        ErrorResponse response = buildError("CONSTRAINT_VIOLATION", "Constraint violation", request);
        response.setFieldErrors(violations);

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    // ─── Missing Required Header ──────────────────────────────────────────────
    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ErrorResponse> handleMissingHeader(
            MissingRequestHeaderException ex, HttpServletRequest request) {
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(buildError(
                        "MISSING_HEADER",
                        "Required header missing: " + ex.getHeaderName(),
                        request
                ));
    }

    // ─── Malformed JSON ───────────────────────────────────────────────────────
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleMalformedJson(
            HttpMessageNotReadableException ex, HttpServletRequest request) {
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(buildError("MALFORMED_REQUEST", "Request body is malformed or unreadable", request));
    }

    // ─── Type Mismatch ────────────────────────────────────────────────────────
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(
            MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(buildError(
                        "TYPE_MISMATCH",
                        String.format("Parameter '%s' should be of type %s",
                                ex.getName(), ex.getRequiredType() != null ? ex.getRequiredType().getSimpleName() : "unknown"),
                        request
                ));
    }

    // ─── Catch-All ────────────────────────────────────────────────────────────
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneral(
            Exception ex, HttpServletRequest request) {
        log.error("Unhandled exception at {}: ", request.getRequestURI(), ex);

        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(buildError(
                        "INTERNAL_ERROR",
                        "An unexpected error occurred. Our team has been notified.",
                        request
                ));
    }

    // ─── Builder Helper ───────────────────────────────────────────────────────
    private ErrorResponse buildError(String code, String message, HttpServletRequest request) {
        return ErrorResponse.builder()
                .errorId(UUID.randomUUID().toString())
                .code(code)
                .message(message)
                .path(request.getRequestURI())
                .method(request.getMethod())
                .timestamp(LocalDateTime.now())
                .build();
    }
}