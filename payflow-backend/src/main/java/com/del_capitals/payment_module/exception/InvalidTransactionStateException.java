package com.del_capitals.payment_module.exception;

public class InvalidTransactionStateException extends PaymentException {

    public InvalidTransactionStateException(String transactionId, String currentState, String expectedState) {
        super(
                String.format("Transaction %s is in state %s, expected %s", transactionId, currentState, expectedState),
                "INVALID_TRANSACTION_STATE",
                422
        );
    }

}
