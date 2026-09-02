package com.example.wallet.exception;

/**
 * Thrown when no wallet is found for the given userId.
 * The controller advice maps this to HTTP 404 Not Found.
 */
public class WalletNotFoundException extends RuntimeException {

    public WalletNotFoundException(String userId) {
        super("Wallet not found for user: " + userId);
    }
}
