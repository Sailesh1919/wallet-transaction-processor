package com.example.wallet.repository;

import com.example.wallet.entity.Wallet;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;

/**
 * WalletRepository – Spring Data JPA repository for the Wallet table.
 *
 * Spring automatically creates the implementation at runtime.
 * We just declare the method signatures.
 *
 * Key method: findByUserIdWithLock
 * ──────────────────────────────────
 * When 10 requests arrive at the same time and all try to debit the same wallet,
 * the database must ensure they don't all read the same old balance.
 *
 * @Lock(LockModeType.PESSIMISTIC_WRITE) tells JPA:
 *   "When you SELECT this wallet row, also add a write lock (SELECT ... FOR UPDATE)."
 *
 * What this means in practice:
 *   - Request 1 locks the row → reads balance ₹500 → deducts ₹100 → saves ₹400 → unlocks
 *   - Request 2 was waiting → now reads ₹400 (the updated balance) → deducts ₹100 → saves ₹300
 *   - ... and so on
 *
 * Without this lock, all 10 requests could read ₹500 at the same time
 * and all subtract ₹100 → all try to save ₹400 → balance never goes below ₹400.
 * That would be wrong (and could let the balance go negative in other scenarios).
 */
public interface WalletRepository extends JpaRepository<Wallet, Long> {

    /**
     * Finds a wallet by userId AND acquires a PESSIMISTIC_WRITE lock on the row.
     *
     * The @Query is needed because Spring Data cannot automatically apply @Lock
     * to a derived query like findByUserId without a custom JPQL query.
     *
     * JPQL syntax: "SELECT w FROM Wallet w WHERE w.userId = :userId"
     * (Wallet is the Java class name, not the table name)
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT w FROM Wallet w WHERE w.userId = :userId")
    Optional<Wallet> findByUserIdWithLock(@Param("userId") String userId);

    /**
     * Simple lookup without a lock – used in tests to read the final balance
     * after all transactions have completed.
     */
    Optional<Wallet> findByUserId(String userId);
}
