# wallet-transaction-processor

A simple Java Spring Boot project that processes wallet debit transactions safely,
handling concurrent requests and duplicate submissions correctly.

Built as a Java Backend .

---

## Technologies Used

| Technology        | Version  | Purpose                                    |
|-------------------|----------|--------------------------------------------|
| Java              | 17       | Programming language                       |
| Spring Boot       | 3.2.5    | Application framework                      |
| Spring Web        | —        | REST API (controllers, routing)            |
| Spring Data JPA   | —        | Database access (repositories, locking)    |
| Hibernate         | —        | ORM – maps Java classes to DB tables       |
| H2 Database       | —        | In-memory database (no setup required)     |
| JUnit 5           | —        | Automated testing                          |
| Maven             | —        | Build tool and dependency management       |

---

## How to Run

### Prerequisites
- Java 17 installed
- Maven installed (or use the Maven wrapper: `./mvnw`)

### Steps

```bash
# Clone or download the project
cd wallet-transaction-processor

# Run the application
mvn spring-boot:run
```

The application starts on `http://localhost:8080`.

You can open the H2 database console in your browser:
```
http://localhost:8080/h2-console
JDBC URL: jdbc:h2:mem:walletdb
Username: sa
Password: (leave empty)
```

---

## How to Run Tests

```bash
mvn test
```

All 3 integration tests run automatically with the H2 in-memory database.
No external database is needed.

**Expected test output:**
```
=== Single Debit Test ===
Starting Balance : ₹1000.00
Deducted         : ₹250.00
Final Balance    : ₹750.0000
=========================

=== Idempotency Test ===
Total Requests   : 3
Successful       : 1
Duplicates       : 2
Final Balance    : ₹750.0000
========================

=== Race Condition Test ===
Total Requests      : 10
Successful          : 5
Insufficient Funds  : 5
Final Balance       : ₹0.0000
Transactions Saved  : 5
==========================
```

---

## API Endpoint

### POST `/api/v1/transactions/process`

**Request Body (JSON):**
```json
{
  "transactionId": "abc-123",
  "userId":        "user-456",
  "amount":        100.00,
  "type":          "DEBIT"
}
```

**Success Response (200 OK):**
```json
{
  "message":          "Transaction processed successfully.",
  "transactionId":    "abc-123",
  "remainingBalance": 400.0000
}
```

**Error Responses:**

| HTTP Status | When                                      |
|-------------|-------------------------------------------|
| 409 Conflict| Same `transactionId` submitted again      |
| 400 Bad Request | Not enough balance in the wallet      |
| 404 Not Found   | No wallet found for this `userId`     |

**Example curl:**
```bash
curl -X POST http://localhost:8080/api/v1/transactions/process \
  -H "Content-Type: application/json" \
  -d '{"transactionId":"tx-001","userId":"user-1","amount":100.00,"type":"DEBIT"}'
```

---

## Idempotency Explanation

**Problem:** The same request can arrive multiple times (e.g., due to network retry).
We should deduct money only once.

**Solution:**
1. The `transactionId` column has a `UNIQUE` database constraint.
2. Before saving, we check `existsByTransactionId()` to handle obvious retries early.
3. If two requests arrive simultaneously and both pass step 2, the database rejects
   the second `INSERT` with a constraint violation.
4. We catch `DataIntegrityViolationException` and return `409 Conflict`.

This means **the database is the ultimate guard**, not just the Java code.

---

## Concurrency / Locking Explanation

**Problem:** If 10 requests try to debit the same wallet at the same time,
they might all read the same old balance and all think there is enough money.

**Example without locking:**
```
All 10 threads read balance = ₹500
All 10 threads check: 500 >= 100 ✓
All 10 threads deduct ₹100 and save ₹400
Final balance = ₹400  ← WRONG! Should be ₹0
```

**Solution:** `PESSIMISTIC_WRITE` lock (`SELECT ... FOR UPDATE`)

When a thread reads the wallet, the database locks that row.
Other threads trying to read the same row must wait until the first thread commits.

```
Thread 1 reads ₹500 → locks row → deducts → saves ₹400 → unlocks
Thread 2 reads ₹400 → locks row → deducts → saves ₹300 → unlocks
...
Thread 5 reads ₹100 → locks row → deducts → saves ₹0   → unlocks
Thread 6 reads ₹0   → InsufficientFundsException (₹0 < ₹100)
...
```

This is implemented with:
```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("SELECT w FROM Wallet w WHERE w.userId = :userId")
Optional<Wallet> findByUserIdWithLock(@Param("userId") String userId);
```

And the service method is annotated with `@Transactional`, which holds the lock
for the entire duration of the method.
