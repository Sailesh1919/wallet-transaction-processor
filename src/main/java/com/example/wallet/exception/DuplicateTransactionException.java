package com.example.wallet.exception;

/**
 * Thrown when the same transactionId is submitted more than once.
 * The controller advice maps this to HTTP 409 Conflict.
 */
public class DuplicateTransactionException extends RuntimeException {

    public DuplicateTransactionException(String transactionId) {
        super("Transaction already processed: " + transactionId);
    }
}
