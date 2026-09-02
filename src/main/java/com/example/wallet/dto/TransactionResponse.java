package com.example.wallet.dto;

import java.math.BigDecimal;

/**
 * TransactionResponse – what the API sends back to the client after processing.
 *
 * Example JSON response (success):
 * {
 *   "message":        "Transaction processed successfully.",
 *   "transactionId":  "abc-123",
 *   "remainingBalance": 400.0000
 * }
 *
 * This is a simple Data Transfer Object (DTO).
 * The service creates one of these and returns it to the controller,
 * which Spring automatically converts to JSON.
 */
public class TransactionResponse {

    private String     message;
    private String     transactionId;
    private BigDecimal remainingBalance;

    // ─── No-arg constructor ─────────────────────────────────────────

    public TransactionResponse() {
    }

    /** Convenience constructor used in the service */
    public TransactionResponse(String message, String transactionId, BigDecimal remainingBalance) {
        this.message          = message;
        this.transactionId    = transactionId;
        this.remainingBalance = remainingBalance;
    }

    // ─── Getters & Setters ──────────────────────────────────────────

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getTransactionId() {
        return transactionId;
    }

    public void setTransactionId(String transactionId) {
        this.transactionId = transactionId;
    }

    public BigDecimal getRemainingBalance() {
        return remainingBalance;
    }

    public void setRemainingBalance(BigDecimal remainingBalance) {
        this.remainingBalance = remainingBalance;
    }
}
