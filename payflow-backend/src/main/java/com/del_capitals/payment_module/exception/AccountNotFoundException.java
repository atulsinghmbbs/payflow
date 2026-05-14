package com.del_capitals.payment_module.exception;

public class AccountNotFoundException extends PaymentException {
    public AccountNotFoundException(String accountId) {
        super("Account not found: " + accountId, "ACCOUNT_NOT_FOUND", 404);
    }
}

