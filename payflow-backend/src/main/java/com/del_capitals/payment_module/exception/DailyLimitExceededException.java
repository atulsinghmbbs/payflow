package com.del_capitals.payment_module.exception;

import java.math.BigDecimal;

public class DailyLimitExceededException extends PaymentException {
    public DailyLimitExceededException(BigDecimal limit, BigDecimal spent) {
        super(
                String.format("Daily transaction limit exceeded. Limit: %.2f, Already spent: %.2f", limit, spent),
                "DAILY_LIMIT_EXCEEDED",
                422
        );
    }
}

