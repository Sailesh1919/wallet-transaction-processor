package com.example.wallet.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import java.util.Map;

/**
 * GlobalExceptionHandler – catches exceptions thrown anywhere in the application
 * and converts them into clean JSON error responses.
 *
 * @RestControllerAdvice is a combination of:
 *   @ControllerAdvice  → applies to all controllers
 *   @ResponseBody      → the return value is automatically written as JSON
 *
 * Without this class, Spring would return a generic HTML error page,
 * which is not useful for a REST API.
 *
 * We use Map.of("error", message) to keep the response body simple:
 * { "error": "some message here" }
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 409 Conflict – same transactionId sent more than once.
     */
    @ExceptionHandler(DuplicateTransactionException.class)
    public ResponseEntity<Map<String, String>> handleDuplicate(DuplicateTransactionException ex) {
        return ResponseEntity
                .status(HttpStatus.CONFLICT)                   // 409
                .body(Map.of("error", ex.getMessage()));
    }

    /**
     * 400 Bad Request – wallet balance is too low.
     */
    @ExceptionHandler(InsufficientFundsException.class)
    public ResponseEntity<Map<String, String>> handleInsufficientFunds(InsufficientFundsException ex) {
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)                // 400
                .body(Map.of("error", ex.getMessage()));
    }

    /**
     * 404 Not Found – no wallet exists for this userId.
     */
    @ExceptionHandler(WalletNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleWalletNotFound(WalletNotFoundException ex) {
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)                  // 404
                .body(Map.of("error", ex.getMessage()));
    }
}
