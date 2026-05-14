package com.del_capitals.payment_module.service;

import com.del_capitals.payment_module.dto.request.TransactionRequest;
import com.del_capitals.payment_module.dto.response.AccountResponse;
import com.del_capitals.payment_module.dto.response.PagedResponse;
import com.del_capitals.payment_module.dto.response.TransactionResponse;
import org.springframework.data.domain.Pageable;

import javax.security.auth.login.AccountNotFoundException;
import java.util.List;
import java.util.UUID;

public interface PaymentService {
    public TransactionResponse initiateTransaction(TransactionRequest request, String idempotencyKey);
    public TransactionResponse reverseTransaction(UUID transactionId) throws AccountNotFoundException;
    public TransactionResponse getTransaction(UUID id);
    public PagedResponse<TransactionResponse> getAccountTransactions(String accountId, Pageable pageable) throws AccountNotFoundException;
    public AccountResponse getAccountBalance(String accountId) throws AccountNotFoundException;
    public List<AccountResponse> getAllAccounts();
}
