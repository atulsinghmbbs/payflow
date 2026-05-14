package com.del_capitals.payment_module.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AccountResponse {
    private String id;
    private String accountNumber;
    private String ownerName;
    private BigDecimal balance;
    private BigDecimal availableBalance;
    private String currency;
    private String status;
}

