package com.example.wallet.exception;

import java.math.BigDecimal;

/**
 * Thrown when the wallet does not have enough balance to complete a DEBIT.
 * The controller advice maps this to HTTP 400 Bad Request.
 */
public class InsufficientFundsException extends RuntimeException {

    public InsufficientFundsException(BigDecimal balance, BigDecimal requested) {
        super("Insufficient funds. Available: " + balance + ", Requested: " + requested);
    }
}
