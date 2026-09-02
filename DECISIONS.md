# DECISIONS.md – Technical Design Decisions

---

## 1. How did you handle the concurrency race condition?

### The Problem

If 10 HTTP requests arrive at the same time, all trying to debit the same wallet,
they could all read the same old balance and all think there is enough money.

Without any protection, the result would be wrong:

```
Wallet balance = ₹500
10 threads read balance = ₹500 simultaneously
All 10 check: 500 >= 100 ✓
All 10 deduct ₹100 and try to save ₹400
Final balance = ₹400  ← WRONG
```

### The Solution: Pessimistic Locking + @Transactional

**Step 1: PESSIMISTIC_WRITE lock on the wallet row**

In `WalletRepository`, the method that fetches the wallet is annotated with:

```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("SELECT w FROM Wallet w WHERE w.userId = :userId")
Optional<Wallet> findByUserIdWithLock(@Param("userId") String userId);
```

When JPA executes this query, it issues `SELECT ... FOR UPDATE` at the database level.
This tells the database: "Lock this row. No other transaction can read it for update until I'm done."

**Step 2: @Transactional in the service method**

```java
@Transactional
public TransactionResponse processTransaction(TransactionRequest request) { ... }
```

`@Transactional` wraps the entire method in a single database transaction.
The PESSIMISTIC_WRITE lock is held from when the wallet is read until the transaction commits.

**How it works in practice:**

```
Thread 1: SELECT wallet FOR UPDATE → gets lock → deducts → saves → commits → releases lock
Thread 2: SELECT wallet FOR UPDATE → BLOCKED, waiting for Thread 1 to release...
Thread 2: (Thread 1 done) → reads UPDATED balance ₹400 → deducts → saves ₹300 → commits
...
Thread 5: reads ₹100 → deducts → saves ₹0 → commits
Thread 6: reads ₹0 → 0 < 100 → throws InsufficientFundsException
```

**Result:**
- 5 requests succeed (₹500 / ₹100 = exactly 5 allowed)
- 5 requests fail with insufficient funds
- Final balance = ₹0
- Balance never goes negative

**Why not use optimistic locking?**

Optimistic locking (using `@Version`) is also valid, but it causes failed transactions to throw
`ObjectOptimisticLockingFailureException`, which must be retried. That makes the logic more complex.
Pessimistic locking blocks instead of retrying, which is simpler to implement and easier to explain.

---

## 2. Where did your AI assistant give you an incorrect or sub-optimal suggestion?

### The Initial (Sub-Optimal) Suggestion

The initial suggestion for handling duplicate transactions was:

```java
// In TransactionService
if (transactionRepository.existsByTransactionId(request.getTransactionId())) {
    throw new DuplicateTransactionException(request.getTransactionId());
}
```

This looks correct at first glance, but it has a race condition flaw.

### Why It Is Not Safe Alone

If 3 requests arrive simultaneously with the same `transactionId`:

```
Thread 1: existsByTransactionId("abc") → false (nothing saved yet)
Thread 2: existsByTransactionId("abc") → false (nothing saved yet)
Thread 3: existsByTransactionId("abc") → false (nothing saved yet)
All 3 pass the check!
Thread 1: saves transaction "abc" ✓
Thread 2: tries to save "abc" → ??? (no protection!)
Thread 3: tries to save "abc" → ??? (no protection!)
```

If the only guard is the Java `existsByTransactionId()` check, two threads could both
pass it and both try to insert the same `transactionId`, resulting in two deductions.

### The Improvement: Database Unique Constraint

The real safety net is the `UNIQUE` constraint on the `transactionId` column in the database:

```java
// In Transaction.java entity
@Column(nullable = false, unique = true)
private String transactionId;
```

When the second thread tries to insert the same `transactionId`, the **database itself** rejects
the insert with a constraint violation. This is caught in the service:

```java
try {
    transactionRepository.save(transaction);
} catch (DataIntegrityViolationException ex) {
    throw new DuplicateTransactionException(request.getTransactionId());
}
```

### Summary

| Approach                          | Safe for simultaneous requests? |
|-----------------------------------|---------------------------------|
| `existsByTransactionId()` only    | ❌ No – race condition           |
| DB `UNIQUE` constraint only       | ✅ Yes – database enforces it    |
| Both together (what we use)       | ✅ Yes – fast check + safe guard |

The `existsByTransactionId()` check is kept as a fast early return for the common case
(a retry that arrives after the first request has already committed).
The DB unique constraint is the actual idempotency guarantee.
