package com.del_capitals.payment_module.controller;


import com.del_capitals.payment_module.dto.request.TransactionRequest;
import com.del_capitals.payment_module.dto.response.AccountResponse;
import com.del_capitals.payment_module.dto.response.PagedResponse;
import com.del_capitals.payment_module.dto.response.TransactionResponse;
import com.del_capitals.payment_module.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.security.auth.login.AccountNotFoundException;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Payments", description = "Payment Transaction Management API")
@CrossOrigin(origins = {"http://localhost:4200", "http://localhost:3000"})
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping("/transactions")
    @Operation(summary = "Initiate a new payment transaction",
            description = "Supports DEBIT, CREDIT, TRANSFER, and REFUND. " +
                    "Pass Idempotency-Key header to safely retry requests.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Transaction created"),
            @ApiResponse(responseCode = "400", description = "Validation error"),
            @ApiResponse(responseCode = "404", description = "Account not found"),
            @ApiResponse(responseCode = "409", description = "Duplicate transaction or concurrent conflict"),
            @ApiResponse(responseCode = "422", description = "Business rule violation"),
            @ApiResponse(responseCode = "429", description = "Rate limit exceeded"),
    })
    public ResponseEntity<TransactionResponse> createTransaction(
            @Valid @RequestBody TransactionRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false)
            @Parameter(description = "Unique key to safely retry requests without duplicate processing")
            String idempotencyKey) {

        log.info("Transaction request: type={}, account={}, amount={} {}",
                request.getType(), request.getAccountId(), request.getAmount(), request.getCurrency());

        TransactionResponse response = paymentService.initiateTransaction(request, idempotencyKey);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/transactions/{id}")
    @Operation(summary = "Get a transaction by ID")
    public ResponseEntity<TransactionResponse> getTransaction(
            @PathVariable UUID id) {
        return ResponseEntity.ok(paymentService.getTransaction(id));
    }

    @GetMapping("/accounts/{accountId}/transactions")
    @Operation(summary = "Get paginated transaction history for an account")
    public ResponseEntity<PagedResponse<TransactionResponse>> getAccountTransactions(
            @PathVariable String accountId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) throws AccountNotFoundException {

        size = Math.min(size, 100);

        Sort sort = sortDir.equalsIgnoreCase("asc")
                ? Sort.by(sortBy).ascending()
                : Sort.by(sortBy).descending();

        return ResponseEntity.ok(paymentService.getAccountTransactions(accountId, PageRequest.of(page, size, sort))
        );
    }

    @GetMapping("/accounts/{accountId}/balance")
    @Operation(summary = "Get real-time account balance")
    public ResponseEntity<AccountResponse> getAccountBalance(
            @PathVariable String accountId) throws AccountNotFoundException {
        return ResponseEntity.ok(paymentService.getAccountBalance(accountId));
    }

    @PostMapping("/transactions/{id}/reverse")
    @Operation(summary = "Reverse a completed transaction")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Transaction reversed"),
            @ApiResponse(responseCode = "404", description = "Transaction not found"),
            @ApiResponse(responseCode = "422", description = "Transaction cannot be reversed"),
            @ApiResponse(responseCode = "409", description = "Concurrent modification conflict"),
    })
    public ResponseEntity<TransactionResponse> reverseTransaction(
            @PathVariable UUID id) throws AccountNotFoundException {
        log.info("Reversal request for transaction: {}", id);
        return ResponseEntity.ok(paymentService.reverseTransaction(id));
    }

    @GetMapping("/accounts")
    public ResponseEntity<List<AccountResponse>> getAllAccounts() {
        List<AccountResponse> accounts = paymentService.getAllAccounts();
        return ResponseEntity.ok(accounts);
    }

    // ─── Health Check ─────────────────────────────────────────────────────────
    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("Payment Service is running");
    }
}
