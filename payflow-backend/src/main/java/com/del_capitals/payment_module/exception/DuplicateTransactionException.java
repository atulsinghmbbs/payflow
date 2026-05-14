package com.del_capitals.payment_module.exception;

public class DuplicateTransactionException extends PaymentException {
    private final String existingTransactionId;

    public DuplicateTransactionException(String idempotencyKey, String existingId) {
        super(
                "Duplicate transaction detected for idempotency key: " + idempotencyKey,
                "DUPLICATE_TRANSACTION",
                409
        );
        this.existingTransactionId = existingId;
    }

    public String getExistingTransactionId() { return existingTransactionId; }
}
