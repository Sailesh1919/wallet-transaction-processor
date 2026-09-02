# Wallet Transaction Processor

A simple Spring Boot application for processing wallet debit transactions.

The project handles:

* Duplicate transaction requests
* Concurrent wallet debit requests
* Insufficient wallet balance
* H2 in-memory database

## Tech Used

* Java 17
* Spring Boot 3.2.5
* Spring Web
* Spring Data JPA
* H2 Database
* JUnit 5
* Maven

## How to Run

Make sure Java 17 is installed.

Run the application:

mvn spring-boot:run

The application runs on:

http://localhost:8080


## Run Tests

mvn test

The tests use an H2 in-memory database, so no external database is required.

The test suite checks:

1. A normal debit transaction
2. Three identical transaction requests sent at the same time
3. Ten concurrent debit requests against a wallet with ₹500

## API

### Process Transaction

POST /api/v1/transactions/process


Example request:

{
  "transactionId": "abc-123",
  "userId": "user-456",
  "amount": 100.00,
  "type": "DEBIT"
}


## Concurrency and Idempotency

`transactionId` is unique in the database, so the same transaction cannot be saved more than once.

For wallet balance updates, a `PESSIMISTIC_WRITE` lock is used on the wallet row. This prevents multiple requests from updating the same wallet balance at the same time.

The balance is checked before every debit, so the wallet cannot go below zero.

## Project Structure

src/main/java
├── controller
├── service
├── repository
├── entity
├── dto
└── exception

src/test/java
└── TransactionIntegrationTest.java

DECISIONS.md
README.md
pom.xml

