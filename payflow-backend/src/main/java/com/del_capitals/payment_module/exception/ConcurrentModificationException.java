package com.del_capitals.payment_module.exception;

public class ConcurrentModificationException extends PaymentException {
    public ConcurrentModificationException(String resourceId) {
        super(
                "Resource is being modified by another transaction. Please retry: " + resourceId,
                "CONCURRENT_MODIFICATION",
                409
        );
    }
}
