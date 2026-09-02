package com.example.wallet.dto;

import com.example.wallet.entity.TransactionType;
import java.math.BigDecimal;

/**
 * TransactionRequest – the JSON body the client sends to the API.
 *
 * Example JSON:
 * {
 *   "transactionId": "abc-123",
 *   "userId":        "user-456",
 *   "amount":        100.00,
 *   "type":          "DEBIT"
 * }
 *
 * This is a simple Data Transfer Object (DTO).
 * It is NOT stored in the database directly.
 * The controller reads it, passes it to the service, and the service does the work.
 */
public class TransactionRequest {

    private String          transactionId;
    private String          userId;
    private BigDecimal      amount;
    private TransactionType type;

    // ─── No-arg constructor (Jackson needs this to parse JSON) ───────

    public TransactionRequest() {
    }

    // ─── Getters & Setters ──────────────────────────────────────────

    public String getTransactionId() {
        return transactionId;
    }

    public void setTransactionId(String transactionId) {
        this.transactionId = transactionId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public TransactionType getType() {
        return type;
    }

    public void setType(TransactionType type) {
        this.type = type;
    }
}
