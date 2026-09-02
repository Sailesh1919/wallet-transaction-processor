package com.example.wallet;

import com.example.wallet.dto.TransactionRequest;
import com.example.wallet.dto.TransactionResponse;
import com.example.wallet.entity.TransactionType;
import com.example.wallet.entity.Wallet;
import com.example.wallet.exception.DuplicateTransactionException;
import com.example.wallet.exception.InsufficientFundsException;
import com.example.wallet.repository.TransactionRepository;
import com.example.wallet.repository.WalletRepository;
import com.example.wallet.service.TransactionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TransactionIntegrationTest – tests the full application stack using a real
 * H2 in-memory database (no mocking of the database).
 *
 * @SpringBootTest starts the entire Spring application context.
 * H2 is used automatically because it is on the classpath and
 * spring.jpa.hibernate.ddl-auto=create-drop creates/drops tables for each test run.
 *
 * What "integration test" means here:
 *   - We test the Service + Repository + Database all together.
 *   - This is different from a unit test, which would test one class in isolation.
 */
@SpringBootTest
class TransactionIntegrationTest {

    // ─── Spring injects these automatically ─────────────────────────
    @Autowired
    private TransactionService     transactionService;

    @Autowired
    private WalletRepository       walletRepository;

    @Autowired
    private TransactionRepository  transactionRepository;

    // ─── Test data ───────────────────────────────────────────────────
    private static final String USER_ID = "test-user-" + UUID.randomUUID();

    /**
     * @BeforeEach runs before EVERY test method.
     * We clean up the database so each test starts with a fresh state.
     */
    @BeforeEach
    void setUp() {
        // Delete all records so tests don't interfere with each other
        transactionRepository.deleteAll();
        walletRepository.deleteAll();
    }

    // ════════════════════════════════════════════════════════════════
    //  TEST 1 – Single valid debit
    // ════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Processes a single valid debit transaction successfully.")
    void testSingleValidDebit() {
        // ── Arrange: create a wallet with ₹1000 ─────────────────────
        BigDecimal startingBalance = new BigDecimal("1000.00");
        Wallet wallet = walletRepository.save(new Wallet(USER_ID, startingBalance));

        // ── Act: debit ₹250 ──────────────────────────────────────────
        BigDecimal debitAmount = new BigDecimal("250.00");
        TransactionRequest request = buildRequest(UUID.randomUUID().toString(), USER_ID, debitAmount);

        TransactionResponse response = transactionService.processTransaction(request);

        // ── Assert ───────────────────────────────────────────────────
        // 1. Response says success
        assertEquals("Transaction processed successfully.", response.getMessage());

        // 2. Remaining balance in the response is correct
        BigDecimal expectedBalance = new BigDecimal("750.00");
        assertEquals(0, expectedBalance.compareTo(response.getRemainingBalance()),
                "Response balance should be ₹750");

        // 3. Database balance is also updated correctly
        Wallet updated = walletRepository.findByUserId(USER_ID).orElseThrow();
        assertEquals(0, expectedBalance.compareTo(updated.getBalance()),
                "DB balance should be ₹750");

        // 4. Transaction record is saved
        assertTrue(transactionRepository.existsByTransactionId(request.getTransactionId()),
                "Transaction should be persisted in DB");

        System.out.println("\n=== Single Debit Test ===");
        System.out.println("Starting Balance : ₹" + startingBalance);
        System.out.println("Deducted         : ₹" + debitAmount);
        System.out.println("Final Balance    : ₹" + updated.getBalance());
        System.out.println("=========================\n");
    }

    // ════════════════════════════════════════════════════════════════
    //  TEST 2 – Idempotency: same transactionId sent 3 times at once
    // ════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Sends 3 identical transactionIDs simultaneously. Ensures the balance is only deducted once.")
    void testIdempotency() throws InterruptedException {
        // ── Arrange: wallet with ₹1000 ───────────────────────────────
        BigDecimal startingBalance = new BigDecimal("1000.00");
        walletRepository.save(new Wallet(USER_ID, startingBalance));

        // All 3 requests use the SAME transactionId
        String sharedTransactionId = UUID.randomUUID().toString();
        BigDecimal debitAmount = new BigDecimal("250.00");

        int totalRequests = 3;

        // ── Concurrency primitives ────────────────────────────────────
        // CountDownLatch(n): a counter that starts at n.
        // Each thread calls latch.countDown() to decrement it.
        // latch.await() blocks until the counter reaches 0.
        // We use this to make all 3 threads start at the same time.
        CountDownLatch startLatch  = new CountDownLatch(1);          // start gun
        CountDownLatch doneLatch   = new CountDownLatch(totalRequests); // wait for all to finish

        // AtomicInteger is a thread-safe integer counter.
        // Regular int++ is NOT safe to use from multiple threads.
        AtomicInteger successCount   = new AtomicInteger(0);
        AtomicInteger duplicateCount = new AtomicInteger(0);

        // ExecutorService manages a pool of threads.
        // newFixedThreadPool(3) creates exactly 3 threads.
        ExecutorService executor = Executors.newFixedThreadPool(totalRequests);

        for (int i = 0; i < totalRequests; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await(); // Wait until the start gun fires

                    TransactionRequest request =
                            buildRequest(sharedTransactionId, USER_ID, debitAmount);

                    transactionService.processTransaction(request);
                    successCount.incrementAndGet();

                } catch (DuplicateTransactionException e) {
                    duplicateCount.incrementAndGet();
                } catch (Exception e) {
                    // Unexpected error – print it so we can debug
                    System.err.println("Unexpected error in idempotency test: " + e.getMessage());
                } finally {
                    doneLatch.countDown(); // Signal that this thread is done
                }
            });
        }

        startLatch.countDown();                     // Fire the start gun – all 3 threads go
        doneLatch.await(10, TimeUnit.SECONDS);      // Wait for all 3 threads to finish
        executor.shutdown();

        // ── Assert ───────────────────────────────────────────────────
        // Exactly 1 request should succeed
        assertEquals(1, successCount.get(),
                "Exactly 1 request should succeed");

        // Exactly 2 should be rejected as duplicates
        assertEquals(2, duplicateCount.get(),
                "Exactly 2 requests should be rejected as duplicates");

        // Only 1 transaction record should exist in the DB
        assertEquals(1, transactionRepository.count(),
                "Only 1 transaction should be saved");

        // Balance should be deducted only once: 1000 - 250 = 750
        BigDecimal expectedBalance = new BigDecimal("750.00");
        Wallet finalWallet = walletRepository.findByUserId(USER_ID).orElseThrow();
        assertEquals(0, expectedBalance.compareTo(finalWallet.getBalance()),
                "Balance should be deducted only once");

        System.out.println("\n=== Idempotency Test ===");
        System.out.println("Total Requests   : " + totalRequests);
        System.out.println("Successful       : " + successCount.get());
        System.out.println("Duplicates       : " + duplicateCount.get());
        System.out.println("Final Balance    : ₹" + finalWallet.getBalance());
        System.out.println("========================\n");
    }

    // ════════════════════════════════════════════════════════════════
    //  TEST 3 – Race condition: 10 concurrent ₹100 debits on ₹500 wallet
    // ════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Sends 10 concurrent debit requests of ₹100 for a wallet with a ₹500 balance. Ensures the final balance is exactly ₹0 and 5 requests fail with insufficient funds.")
    void testConcurrentDebits() throws InterruptedException {
        // ── Arrange: wallet with exactly ₹500 ───────────────────────
        BigDecimal startingBalance = new BigDecimal("500.00");
        walletRepository.save(new Wallet(USER_ID, startingBalance));

        int totalRequests = 10;
        BigDecimal debitAmount = new BigDecimal("100.00");

        // ── Concurrency primitives ────────────────────────────────────
        CountDownLatch startLatch         = new CountDownLatch(1);
        CountDownLatch doneLatch          = new CountDownLatch(totalRequests);
        AtomicInteger  successCount       = new AtomicInteger(0);
        AtomicInteger  insufficientCount  = new AtomicInteger(0);

        ExecutorService executor = Executors.newFixedThreadPool(totalRequests);

        for (int i = 0; i < totalRequests; i++) {
            // Each request uses a UNIQUE transactionId.
            // This test is about race conditions on the balance, not idempotency.
            final String uniqueTxId = UUID.randomUUID().toString();

            executor.submit(() -> {
                try {
                    startLatch.await(); // All threads wait here until the start gun fires

                    TransactionRequest request =
                            buildRequest(uniqueTxId, USER_ID, debitAmount);

                    transactionService.processTransaction(request);
                    successCount.incrementAndGet();

                } catch (InsufficientFundsException e) {
                    insufficientCount.incrementAndGet();
                } catch (Exception e) {
                    System.err.println("Unexpected error in race condition test: " + e.getMessage());
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();                    // Fire! All 10 threads start simultaneously
        doneLatch.await(15, TimeUnit.SECONDS);     // Wait for all threads to finish
        executor.shutdown();

        // ── Read final state from DB ──────────────────────────────────
        Wallet finalWallet = walletRepository.findByUserId(USER_ID).orElseThrow();
        BigDecimal finalBalance = finalWallet.getBalance();
        long savedTransactions  = transactionRepository.count();

        // ── Assert ───────────────────────────────────────────────────

        // 5 requests should succeed (₹500 / ₹100 = 5)
        assertEquals(5, successCount.get(),
                "Exactly 5 requests should succeed");

        // 5 requests should fail with insufficient funds
        assertEquals(5, insufficientCount.get(),
                "Exactly 5 requests should fail with insufficient funds");

        // Final balance must be exactly ₹0
        assertEquals(0, BigDecimal.ZERO.compareTo(finalBalance),
                "Final balance should be ₹0");

        // Balance must NEVER be negative
        assertTrue(finalBalance.compareTo(BigDecimal.ZERO) >= 0,
                "Balance must never be negative");

        // Exactly 5 transactions should be saved (one per successful debit)
        assertEquals(5, savedTransactions,
                "Exactly 5 transaction records should be saved");

        System.out.println("\n=== Race Condition Test ===");
        System.out.println("Total Requests      : " + totalRequests);
        System.out.println("Successful          : " + successCount.get());
        System.out.println("Insufficient Funds  : " + insufficientCount.get());
        System.out.println("Final Balance       : ₹" + finalBalance);
        System.out.println("Transactions Saved  : " + savedTransactions);
        System.out.println("==========================\n");
    }

    // ────────────────────────────────────────────────────────────────
    /** Helper: builds a TransactionRequest with the given values */
    private TransactionRequest buildRequest(String transactionId,
                                            String userId,
                                            BigDecimal amount) {
        TransactionRequest req = new TransactionRequest();
        req.setTransactionId(transactionId);
        req.setUserId(userId);
        req.setAmount(amount);
        req.setType(TransactionType.DEBIT);
        return req;
    }
}
