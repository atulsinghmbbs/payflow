package com.del_capitals.payment_module.exception;

public class SelfTransferException extends PaymentException {
    public SelfTransferException() {
        super("Cannot transfer to the same account", "SELF_TRANSFER", 422);
    }
}

