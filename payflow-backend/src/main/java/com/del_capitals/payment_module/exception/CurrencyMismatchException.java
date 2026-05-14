package com.del_capitals.payment_module.exception;

public class CurrencyMismatchException extends PaymentException {
    public CurrencyMismatchException(String fromCurrency, String toCurrency) {
        super(
                String.format("Currency mismatch: %s vs %s. Cross-currency transfers not supported.",
                        fromCurrency, toCurrency),
                "CURRENCY_MISMATCH",
                422
        );
    }
}

