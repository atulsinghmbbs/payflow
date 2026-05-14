package com.del_capitals.payment_module.exception;


public class TransactionNotFoundException extends PaymentException {
    public TransactionNotFoundException(String id) {
        super("Transaction not found: " + id, "TRANSACTION_NOT_FOUND", 404);
    }
}

