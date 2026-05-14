package com.del_capitals.payment_module.dto.response;

import com.del_capitals.payment_module.enumeration.TransactionStatus;
import com.del_capitals.payment_module.enumeration.TransactionType;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TransactionResponse {
    private UUID id;
    private String referenceId;
    private String accountId;
    private String toAccountId;
    private BigDecimal amount;
    private String currency;
    private TransactionType type;
    private TransactionStatus status;
    private String description;
    private String failureReason;
    private LocalDateTime createdAt;
    private LocalDateTime processedAt;
    private Long version;
}
