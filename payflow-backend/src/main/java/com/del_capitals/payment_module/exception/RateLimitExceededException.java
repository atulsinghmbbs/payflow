package com.del_capitals.payment_module.exception;

public class RateLimitExceededException extends PaymentException {
    public RateLimitExceededException(String accountId) {
        super(
                "Too many transactions. Please wait before initiating new transactions for account: " + accountId,
                "RATE_LIMIT_EXCEEDED",
                429
        );
    }
}

