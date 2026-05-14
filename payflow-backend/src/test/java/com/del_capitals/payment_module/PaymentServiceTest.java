package com.del_capitals.payment_module;


import com.del_capitals.payment_module.domain.Account;
import com.del_capitals.payment_module.dto.request.TransactionRequest;
import com.del_capitals.payment_module.dto.response.TransactionResponse;
import com.del_capitals.payment_module.enumeration.AccountStatus;
import com.del_capitals.payment_module.enumeration.TransactionStatus;
import com.del_capitals.payment_module.enumeration.TransactionType;
import com.del_capitals.payment_module.exception.AccountNotFoundException;
import com.del_capitals.payment_module.exception.DuplicateTransactionException;
import com.del_capitals.payment_module.exception.InsufficientFundsException;
import com.del_capitals.payment_module.exception.SelfTransferException;
import com.del_capitals.payment_module.repository.AccountRepository;
import com.del_capitals.payment_module.repository.TransactionRepository;
import com.del_capitals.payment_module.service.PaymentService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@DisplayName("Payment Service Tests")
class PaymentServiceTest {

    @Autowired private PaymentService paymentService;
    @Autowired private AccountRepository accountRepository;
    @Autowired private TransactionRepository transactionRepository;

    private Account testAccount;
    private Account targetAccount;

    @BeforeEach
    void setUp() {
        testAccount = accountRepository.save(Account.builder()
                .id("TEST-" + UUID.randomUUID())
                .accountNumber("TEST-ACC-" + System.currentTimeMillis())
                .ownerId("OWNER-1")
                .ownerName("Test User")
                .balance(new BigDecimal("10000.00"))
                .availableBalance(new BigDecimal("10000.00"))
                .currency("USD")
                .dailyLimit(new BigDecimal("50000.00"))
                .dailySpent(BigDecimal.ZERO)
                .status(AccountStatus.ACTIVE)
                .build());

        targetAccount = accountRepository.save(Account.builder()
                .id("TGT-" + UUID.randomUUID())
                .accountNumber("TGT-ACC-" + System.currentTimeMillis())
                .ownerId("OWNER-2")
                .ownerName("Target User")
                .balance(new BigDecimal("5000.00"))
                .availableBalance(new BigDecimal("5000.00"))
                .currency("USD")
                .dailyLimit(new BigDecimal("50000.00"))
                .dailySpent(BigDecimal.ZERO)
                .status(AccountStatus.ACTIVE)
                .build());
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // HAPPY PATH TESTS
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Should complete a DEBIT transaction successfully")
    void testDebitTransaction() {
        TransactionRequest request = TransactionRequest.builder()
                .accountId(testAccount.getId())
                .amount(new BigDecimal("500.00"))
                .currency("USD")
                .type(TransactionType.DEBIT)
                .description("Test debit")
                .build();

        TransactionResponse response = paymentService.initiateTransaction(request, null);

        assertThat(response.getStatus()).isEqualTo(TransactionStatus.COMPLETED);
        assertThat(response.getAmount()).isEqualByComparingTo("500.00");

        Account updated = accountRepository.findById(testAccount.getId()).orElseThrow();
        assertThat(updated.getBalance()).isEqualByComparingTo("9500.00");
    }

    @Test
    @DisplayName("Should complete a TRANSFER between accounts")
    void testTransferTransaction() {
        BigDecimal transferAmount = new BigDecimal("1000.00");
        TransactionRequest request = TransactionRequest.builder()
                .accountId(testAccount.getId())
                .toAccountId(targetAccount.getId())
                .amount(transferAmount)
                .currency("USD")
                .type(TransactionType.TRANSFER)
                .build();

        TransactionResponse response = paymentService.initiateTransaction(request, null);

        assertThat(response.getStatus()).isEqualTo(TransactionStatus.COMPLETED);

        Account from = accountRepository.findById(testAccount.getId()).orElseThrow();
        Account to = accountRepository.findById(targetAccount.getId()).orElseThrow();

        assertThat(from.getBalance()).isEqualByComparingTo("9000.00");
        assertThat(to.getBalance()).isEqualByComparingTo("6000.00");
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // IDEMPOTENCY TESTS
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Should reject duplicate requests with same idempotency key")
    void testIdempotencyKeyPreventsDoubleCharge() {
        String idempotencyKey = "IDEM-" + UUID.randomUUID();

        TransactionRequest request = TransactionRequest.builder()
                .accountId(testAccount.getId())
                .amount(new BigDecimal("100.00"))
                .currency("USD")
                .type(TransactionType.DEBIT)
                .build();

        // First request succeeds
        paymentService.initiateTransaction(request, idempotencyKey);

        // Second request with same key should throw
        assertThrows(DuplicateTransactionException.class, () ->
                paymentService.initiateTransaction(request, idempotencyKey)
        );

        // Balance should only be debited once
        Account account = accountRepository.findById(testAccount.getId()).orElseThrow();
        assertThat(account.getBalance()).isEqualByComparingTo("9900.00");
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // ERROR HANDLING TESTS
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Should reject transaction with insufficient funds")
    void testInsufficientFunds() {
        TransactionRequest request = TransactionRequest.builder()
                .accountId(testAccount.getId())
                .amount(new BigDecimal("99999.00"))
                .currency("USD")
                .type(TransactionType.DEBIT)
                .build();

        assertThrows(InsufficientFundsException.class, () ->
                paymentService.initiateTransaction(request, null)
        );

        // Balance must remain unchanged
        Account account = accountRepository.findById(testAccount.getId()).orElseThrow();
        assertThat(account.getBalance()).isEqualByComparingTo("10000.00");
    }

    @Test
    @DisplayName("Should reject transaction for non-existent account")
    void testAccountNotFound() {
        TransactionRequest request = TransactionRequest.builder()
                .accountId("NONEXISTENT-ACCOUNT")
                .amount(new BigDecimal("100.00"))
                .currency("USD")
                .type(TransactionType.DEBIT)
                .build();

        assertThrows(AccountNotFoundException.class, () ->
                paymentService.initiateTransaction(request, null)
        );
    }

    @Test
    @DisplayName("Should reject self-transfer")
    void testSelfTransferRejected() {
        TransactionRequest request = TransactionRequest.builder()
                .accountId(testAccount.getId())
                .toAccountId(testAccount.getId())
                .amount(new BigDecimal("100.00"))
                .currency("USD")
                .type(TransactionType.TRANSFER)
                .build();

        assertThrows(SelfTransferException.class, () ->
                paymentService.initiateTransaction(request, null)
        );
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // CONCURRENCY TESTS — The crown jewel of this test suite
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("CONCURRENCY: Multiple simultaneous debits should not overdraw account")
    void testConcurrentDebitsDoNotOverdrawAccount() throws InterruptedException {
        // Account has $10,000. Fire 20 concurrent $1000 debits.
        // Expected: exactly 10 succeed, 10 fail with InsufficientFunds or similar.
        int threads = 20;
        BigDecimal debitAmount = new BigDecimal("1000.00");

        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch startLatch = new CountDownLatch(1);  // All start simultaneously
        CountDownLatch doneLatch = new CountDownLatch(threads);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        for (int i = 0; i < threads; i++) {
            final int txnNum = i;
            executor.submit(() -> {
                try {
                    startLatch.await(); // Wait for all threads to be ready
                    TransactionRequest req = TransactionRequest.builder()
                            .accountId(testAccount.getId())
                            .amount(debitAmount)
                            .currency("USD")
                            .type(TransactionType.DEBIT)
                            .description("Concurrent txn " + txnNum)
                            .build();
                    paymentService.initiateTransaction(req, "CONC-" + txnNum + "-" + testAccount.getId());
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    failCount.incrementAndGet();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown(); // Fire all threads at once
        doneLatch.await(30, TimeUnit.SECONDS);
        executor.shutdown();

        // CRITICAL ASSERTION: Balance must NEVER go below 0
        Account finalAccount = accountRepository.findById(testAccount.getId()).orElseThrow();
        assertThat(finalAccount.getBalance()).isGreaterThanOrEqualTo(BigDecimal.ZERO);
        assertThat(finalAccount.getBalance()).isLessThanOrEqualTo(new BigDecimal("10000.00"));

        // Success count * 1000 == amount debited
        BigDecimal expectedBalance = new BigDecimal("10000.00")
                .subtract(new BigDecimal(successCount.get()).multiply(debitAmount));
        assertThat(finalAccount.getBalance()).isEqualByComparingTo(expectedBalance);

        System.out.printf("Concurrent debits: %d succeeded, %d failed. Final balance: %s%n",
                successCount.get(), failCount.get(), finalAccount.getBalance());
    }

    @Test
    @DisplayName("CONCURRENCY: Simultaneous transfers are atomic and consistent")
    void testConcurrentTransfersAreAtomic() throws InterruptedException {
        BigDecimal initialFromBalance = testAccount.getBalance();
        BigDecimal initialToBalance = targetAccount.getBalance();
        BigDecimal totalBefore = initialFromBalance.add(initialToBalance);

        int threads = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threads);

        for (int i = 0; i < threads; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    TransactionRequest req = TransactionRequest.builder()
                            .accountId(testAccount.getId())
                            .toAccountId(targetAccount.getId())
                            .amount(new BigDecimal("500.00"))
                            .currency("USD")
                            .type(TransactionType.TRANSFER)
                            .build();
                    paymentService.initiateTransaction(req, null);
                } catch (Exception ignored) {
                    // Some may fail — that's acceptable
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        doneLatch.await(30, TimeUnit.SECONDS);
        executor.shutdown();

        Account from = accountRepository.findById(testAccount.getId()).orElseThrow();
        Account to = accountRepository.findById(targetAccount.getId()).orElseThrow();
        BigDecimal totalAfter = from.getBalance().add(to.getBalance());

        // INVARIANT: Total money in the system must be conserved
        assertThat(totalAfter).isEqualByComparingTo(totalBefore);
        // No negative balances
        assertThat(from.getBalance()).isGreaterThanOrEqualTo(BigDecimal.ZERO);
        assertThat(to.getBalance()).isGreaterThanOrEqualTo(BigDecimal.ZERO);

        System.out.printf("Money conserved: Before=%s, After=%s%n", totalBefore, totalAfter);
    }

    @Test
    @DisplayName("Should successfully reverse a completed transaction")
    void testTransactionReversal() throws javax.security.auth.login.AccountNotFoundException {
        TransactionRequest request = TransactionRequest.builder()
                .accountId(testAccount.getId())
                .amount(new BigDecimal("300.00"))
                .currency("USD")
                .type(TransactionType.DEBIT)
                .build();

        TransactionResponse original = paymentService.initiateTransaction(request, null);
        Account afterDebit = accountRepository.findById(testAccount.getId()).orElseThrow();
        assertThat(afterDebit.getBalance()).isEqualByComparingTo("9700.00");

        paymentService.reverseTransaction(original.getId());

        Account afterReversal = accountRepository.findById(testAccount.getId()).orElseThrow();
        assertThat(afterReversal.getBalance()).isEqualByComparingTo("10000.00");
    }
}