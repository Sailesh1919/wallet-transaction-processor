# Decision Log

## 1. How did you handle the concurrency race condition?

I used `PESSIMISTIC_WRITE` locking for the wallet.

When a debit request arrives the wallet row is locked while the balance is checked and updated. Other requests for the wallet wait until the current transaction is done.

The processing method uses `@Transactional` so the lock stays in place while the balance is updated.

For example with a ₹500 balance and ten ₹100 debit requests five requests can go through. The other five see that the balance is not enough and fail.

I also made sure that `transactionId` is unique in the database so the same transaction cannot be saved more than once.

## 2. Where did your AI assistant give you an sub-optimal suggestion?

The AI assistant first suggested using existsByTransactionId()` to check for duplicate transactions.

I understood this was not enough when multiple requests came in at the time because they could all pass the check before any transaction was saved.

So I added a constraint to `transactionId` in the database. The database then stops the transaction from being inserted more than once.

This made the solution more secure, for duplicate requests.
