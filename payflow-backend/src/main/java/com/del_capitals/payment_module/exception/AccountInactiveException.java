package com.del_capitals.payment_module.exception;

public class AccountInactiveException extends PaymentException {
    public AccountInactiveException(String accountId, String status) {
        super(
                String.format("Account %s is not active. Current status: %s", accountId, status),
                "ACCOUNT_INACTIVE",
                403
        );
    }
}

