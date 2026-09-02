package com.example.wallet.repository;

import com.example.wallet.entity.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

/**
 * TransactionRepository – Spring Data JPA repository for the Transaction table.
 *
 * Spring automatically provides: save(), findById(), findAll(), count(), delete(), etc.
 * We only add the methods we actually need.
 */
public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    /**
     * Check whether a transaction with this ID already exists.
     *
     * IMPORTANT — this check alone is NOT enough for idempotency!
     * If two requests arrive at the same millisecond, both could pass this
     * check before either one has saved.  The real safety net is the
     * UNIQUE constraint on the transactionId column in the database.
     * The database will reject the second insert with a constraint violation,
     * which we catch and convert into a 409 Conflict response.
     *
     * We keep this check anyway because it handles the common case
     * (a retry that arrives after the first request has already committed)
     * without hitting the database constraint.
     */
    boolean existsByTransactionId(String transactionId);

    /**
     * Find a transaction by its transactionId – used in tests to verify
     * that exactly one record was saved.
     */
    Optional<Transaction> findByTransactionId(String transactionId);
}
