package com.example.wallet.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;

/**
 * Transaction entity – represents a single money transaction stored in the database.
 *
 * Key design decisions:
 * 1. transactionId is UNIQUE in the database.
 *    This prevents duplicate transactions even if two requests arrive at the same time.
 *    If we relied only on a Java check (existsByTransactionId), two threads could both
 *    pass the check before either one saves — so the DB constraint is the real safety net.
 *
 * 2. We store the amount that was actually deducted, so we have an audit trail.
 */
@Entity
@Table(name = "transactions")
public class Transaction {

    /** Primary key – auto-incremented by the database */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * The client-supplied transaction ID (usually a UUID).
     *
     * unique = true → H2 (and any real DB) will reject a duplicate insert at the DB level.
     * nullable = false → every transaction must have an ID.
     */
    @Column(nullable = false, unique = true)
    private String transactionId;

    /** The user who initiated this transaction */
    @Column(nullable = false)
    private String userId;

    /** The money amount involved (BigDecimal for accuracy) */
    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    /**
     * DEBIT or CREDIT.
     * @Enumerated(EnumType.STRING) stores "DEBIT" as text in the DB
     * instead of a number (0, 1, …). Text is easier to read and debug.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TransactionType type;

    // ─── Constructors ───────────────────────────────────────────────

    /** No-arg constructor required by JPA */
    public Transaction() {
    }

    /** Convenience constructor used in the service */
    public Transaction(String transactionId, String userId, BigDecimal amount, TransactionType type) {
        this.transactionId = transactionId;
        this.userId        = userId;
        this.amount        = amount;
        this.type          = type;
    }

    // ─── Getters & Setters ──────────────────────────────────────────

    public Long getId() {
        return id;
    }

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
