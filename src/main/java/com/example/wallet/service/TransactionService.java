package com.example.wallet.service;

import com.example.wallet.dto.TransactionRequest;
import com.example.wallet.dto.TransactionResponse;
import com.example.wallet.entity.Transaction;
import com.example.wallet.entity.Wallet;
import com.example.wallet.exception.DuplicateTransactionException;
import com.example.wallet.exception.InsufficientFundsException;
import com.example.wallet.exception.WalletNotFoundException;
import com.example.wallet.repository.TransactionRepository;
import com.example.wallet.repository.WalletRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * TransactionService – contains ALL the business logic.
 *
 * The controller simply receives the HTTP request and delegates here.
 * This class does NOT know anything about HTTP.
 *
 * @Service tells Spring to manage this class as a bean
 * (i.e., Spring creates one instance and wires it wherever it's needed).
 */
@Service
public class TransactionService {

    // ─── Dependencies injected via constructor ───────────────────────
    // Constructor injection is preferred over @Autowired on fields
    // because it makes dependencies explicit and easy to test.

    private final WalletRepository      walletRepository;
    private final TransactionRepository transactionRepository;

    public TransactionService(WalletRepository walletRepository,
                              TransactionRepository transactionRepository) {
        this.walletRepository      = walletRepository;
        this.transactionRepository = transactionRepository;
    }

    // ────────────────────────────────────────────────────────────────
    /**
     * Processes a debit transaction.
     *
     * @Transactional means:
     *   - All database operations inside this method happen in ONE database transaction.
     *   - If anything goes wrong, ALL changes are rolled back automatically.
     *   - The PESSIMISTIC_WRITE lock acquired by findByUserIdWithLock() is held
     *     until this method returns (i.e., until the transaction commits).
     *     This is what prevents two threads from reading the same old balance.
     *
     * Step-by-step flow:
     * ─────────────────
     * 1. Fast idempotency check   → avoid unnecessary DB work for obvious duplicates
     * 2. Find and LOCK wallet     → prevents concurrent reads of the same balance
     * 3. Check sufficient funds   → throw if balance < requested amount
     * 4. Deduct amount            → update the balance in memory
     * 5. Save wallet              → persist the new balance
     * 6. Save transaction         → persist the transaction record
     *    (DB unique constraint on transactionId is the real idempotency guard here)
     * 7. Return response          → message + remaining balance
     */
    @Transactional
    public TransactionResponse processTransaction(TransactionRequest request) {

        // ── Step 1: Fast idempotency check ──────────────────────────
        // If this transactionId was already processed (in a previous committed
        // transaction), return 409 immediately without touching the wallet.
        //
        // NOTE: This check alone is NOT safe for truly simultaneous requests.
        // Two threads could both pass this check before either saves.
        // The DB unique constraint on transactionId handles that edge case.
        if (transactionRepository.existsByTransactionId(request.getTransactionId())) {
            throw new DuplicateTransactionException(request.getTransactionId());
        }

        // ── Step 2: Find wallet + acquire PESSIMISTIC_WRITE lock ─────
        // This issues "SELECT ... FOR UPDATE" at the database level.
        // Other threads that call this method for the SAME userId will block here
        // until the current thread commits or rolls back.
        // This ensures each thread sees the latest balance, not a stale cached copy.
        Wallet wallet = walletRepository
                .findByUserIdWithLock(request.getUserId())
                .orElseThrow(() -> new WalletNotFoundException(request.getUserId()));

        // ── Step 3: Check sufficient funds ──────────────────────────
        // compareTo returns negative if balance < amount
        if (wallet.getBalance().compareTo(request.getAmount()) < 0) {
            throw new InsufficientFundsException(wallet.getBalance(), request.getAmount());
        }

        // ── Step 4: Deduct the amount ────────────────────────────────
        // BigDecimal.subtract() returns a new BigDecimal (immutable).
        // We set the result back on the wallet object.
        wallet.setBalance(wallet.getBalance().subtract(request.getAmount()));

        // ── Step 5: Save the updated wallet balance ──────────────────
        walletRepository.save(wallet);

        // ── Step 6: Save the transaction record ──────────────────────
        // If two threads somehow both passed Step 1 simultaneously,
        // the DB unique constraint on transactionId will reject the second
        // insert here, causing a DataIntegrityViolationException.
        // We catch that and throw our own DuplicateTransactionException.
        try {
            Transaction transaction = new Transaction(
                    request.getTransactionId(),
                    request.getUserId(),
                    request.getAmount(),
                    request.getType()
            );
            transactionRepository.save(transaction);
        } catch (DataIntegrityViolationException ex) {
            // This is the safety net for simultaneous duplicate requests.
            // The DB constraint fired because transactionId must be unique.
            throw new DuplicateTransactionException(request.getTransactionId());
        }

        // ── Step 7: Return a success response ────────────────────────
        return new TransactionResponse(
                "Transaction processed successfully.",
                request.getTransactionId(),
                wallet.getBalance()
        );
    }
}
