package com.example.wallet.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;

/**
 * Wallet entity – represents a user's wallet stored in the database.
 *
 * Each user has one wallet.
 * The wallet holds a balance (using BigDecimal for accurate money math).
 *
 * @Entity  → tells JPA this class maps to a database table
 * @Table   → names the table "wallets"
 */
@Entity
@Table(name = "wallets")
public class Wallet {

    /** Primary key – auto-incremented by the database */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * The user who owns this wallet.
     * We store it as a String (UUID) to keep things simple.
     * nullable = false → a wallet must always have an owner
     */
    @Column(nullable = false)
    private String userId;

    /**
     * Current balance of the wallet.
     * BigDecimal is the right type for money because it avoids
     * the floating-point rounding errors that double or float have.
     *
     * precision = 19, scale = 4 → supports values like 999999999.9999
     */
    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal balance;

    // ─── Constructors ───────────────────────────────────────────────

    /** No-arg constructor required by JPA */
    public Wallet() {
    }

    /** Convenience constructor used in tests and data setup */
    public Wallet(String userId, BigDecimal balance) {
        this.userId  = userId;
        this.balance = balance;
    }

    // ─── Getters & Setters ──────────────────────────────────────────

    public Long getId() {
        return id;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public BigDecimal getBalance() {
        return balance;
    }

    public void setBalance(BigDecimal balance) {
        this.balance = balance;
    }
}
