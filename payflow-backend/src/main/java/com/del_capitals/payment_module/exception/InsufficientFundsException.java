package com.del_capitals.payment_module.exception;

import java.math.BigDecimal;

public class InsufficientFundsException extends PaymentException {
    private final BigDecimal availableBalance;
    private final BigDecimal requestedAmount;

    public InsufficientFundsException(BigDecimal available, BigDecimal requested) {
        super(
                String.format("Insufficient funds. Available: %.2f, Requested: %.2f", available, requested),
                "INSUFFICIENT_FUNDS",
                422
        );
        this.availableBalance = available;
        this.requestedAmount = requested;
    }

    public BigDecimal getAvailableBalance() { return availableBalance; }
    public BigDecimal getRequestedAmount() { return requestedAmount; }
}

