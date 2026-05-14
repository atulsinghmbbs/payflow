package com.del_capitals.payment_module.service.serviceImpl;

import com.del_capitals.payment_module.domain.Account;
import com.del_capitals.payment_module.domain.Transaction;

import com.del_capitals.payment_module.dto.request.TransactionRequest;
import com.del_capitals.payment_module.dto.response.AccountResponse;
import com.del_capitals.payment_module.dto.response.PagedResponse;
import com.del_capitals.payment_module.dto.response.TransactionResponse;
import com.del_capitals.payment_module.enumeration.AccountStatus;
import com.del_capitals.payment_module.enumeration.TransactionStatus;
import com.del_capitals.payment_module.enumeration.TransactionType;
import com.del_capitals.payment_module.exception.*;
import com.del_capitals.payment_module.repository.AccountRepository;
import com.del_capitals.payment_module.repository.TransactionRepository;
import com.del_capitals.payment_module.service.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.stream.Collectors;


@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentServiceImpl implements PaymentService {

        private final TransactionRepository transactionRepository;
        private final AccountRepository accountRepository;


        private final ConcurrentHashMap<String, Semaphore> accountSemaphores = new ConcurrentHashMap<>();
        private static final int MAX_CONCURRENT_PER_ACCOUNT = 3;
        private static final int MAX_HOURLY_TRANSACTIONS = 50;


        @Retryable(
                retryFor = {OptimisticLockingFailureException.class},
                maxAttempts = 3,
                backoff = @Backoff(delay = 100, multiplier = 2, maxDelay = 1000)
        )
        @Transactional(isolation = Isolation.READ_COMMITTED, timeout = 30)
        public TransactionResponse initiateTransaction(TransactionRequest request, String idempotencyKey) throws AccountNotFoundException {

            if (idempotencyKey != null && !idempotencyKey.isBlank()) {
                transactionRepository.findByIdempotencyKey(idempotencyKey).ifPresent(existing -> {
                    log.info("Duplicate request detected for idempotency key: {}", idempotencyKey);
                    throw new DuplicateTransactionException(idempotencyKey, existing.getId().toString());
                });
            }

            Semaphore semaphore = accountSemaphores.computeIfAbsent(
                    request.getAccountId(),
                    k -> new Semaphore(MAX_CONCURRENT_PER_ACCOUNT, true) // fair = FIFO ordering
            );

            boolean acquired = false;
            try {
                acquired = semaphore.tryAcquire();
                if (!acquired) {
                    throw new RateLimitExceededException(request.getAccountId());
                }

                return processTransaction(request, idempotencyKey);

            } finally {
                if (acquired) semaphore.release();
            }
        }


        private TransactionResponse processTransaction(TransactionRequest request, String idempotencyKey) throws AccountNotFoundException {

            Account fromAccount = accountRepository.findByIdWithPessimisticLock(request.getAccountId())
                    .orElseThrow(() -> new AccountNotFoundException(request.getAccountId()));

            if (fromAccount.getStatus() != AccountStatus.ACTIVE) {
                throw new AccountInactiveException(request.getAccountId(), fromAccount.getStatus().name());
            }

            long recentCount = transactionRepository.countRecentTransactions(
                    request.getAccountId(), LocalDateTime.now().minusHours(1)
            );
            if (recentCount >= MAX_HOURLY_TRANSACTIONS) {
                throw new RateLimitExceededException(request.getAccountId());
            }

            if (request.getType() == TransactionType.DEBIT || request.getType() == TransactionType.TRANSFER) {
                BigDecimal dailyDebits = transactionRepository.sumDailyDebits(
                        request.getAccountId(), LocalDateTime.now().withHour(0).withMinute(0)
                );
                BigDecimal projectedSpend = dailyDebits.add(request.getAmount());
                if (projectedSpend.compareTo(fromAccount.getDailyLimit()) > 0) {
                    throw new DailyLimitExceededException(fromAccount.getDailyLimit(), dailyDebits);
                }
            }

            Account toAccount = null;
            if (request.getType() == TransactionType.TRANSFER) {
                if (request.getToAccountId() == null || request.getToAccountId().isBlank()) {
                    throw new PaymentException("Destination account required for transfer",
                            "MISSING_TO_ACCOUNT", 422);
                }
                if (request.getAccountId().equals(request.getToAccountId())) {
                    throw new SelfTransferException();
                }

                String firstId = request.getAccountId().compareTo(request.getToAccountId()) < 0
                        ? request.getAccountId() : request.getToAccountId();
                String secondId = firstId.equals(request.getAccountId())
                        ? request.getToAccountId() : request.getAccountId();

                Account first = accountRepository.findByIdWithPessimisticLock(firstId)
                        .orElseThrow(() -> new AccountNotFoundException(firstId));
                Account second = accountRepository.findByIdWithPessimisticLock(secondId)
                        .orElseThrow(() -> new AccountNotFoundException(secondId));

                toAccount = first.getId().equals(request.getToAccountId()) ? first : second;

                if (toAccount.getStatus() != AccountStatus.ACTIVE) {
                    throw new AccountInactiveException(request.getToAccountId(), toAccount.getStatus().name());
                }

                if (!fromAccount.getCurrency().equals(toAccount.getCurrency())) {
                    throw new CurrencyMismatchException(fromAccount.getCurrency(), toAccount.getCurrency());
                }
            }


            if ((request.getType() == TransactionType.DEBIT || request.getType() == TransactionType.TRANSFER)
                    && fromAccount.getAvailableBalance().compareTo(request.getAmount()) < 0) {
                throw new InsufficientFundsException(fromAccount.getAvailableBalance(), request.getAmount());
            }

            Transaction transaction = Transaction.builder()
                    .referenceId("TXN-" + UUID.randomUUID().toString().substring(0, 12).toUpperCase())
                    .accountId(request.getAccountId())
                    .toAccountId(request.getToAccountId())
                    .amount(request.getAmount())
                    .currency(request.getCurrency())
                    .type(request.getType())
                    .status(TransactionStatus.PROCESSING)
                    .description(request.getDescription())
                    .idempotencyKey(idempotencyKey)
                    .build();

            transaction = transactionRepository.save(transaction);

            try {
                applyBalanceChanges(request, fromAccount, toAccount, transaction);
                transaction.setStatus(TransactionStatus.COMPLETED);
                transaction.setProcessedAt(LocalDateTime.now());
            } catch (Exception ex) {
                transaction.setStatus(TransactionStatus.FAILED);
                transaction.setFailureReason(ex.getMessage());
                transactionRepository.save(transaction);
                throw ex;
            }

            Transaction saved = transactionRepository.save(transaction);
            log.info("Transaction {} completed: {} {} from account {}",
                    saved.getReferenceId(), saved.getAmount(), saved.getCurrency(), saved.getAccountId());

            return mapToResponse(saved);
        }

        private void applyBalanceChanges(TransactionRequest request, Account from,
                                         Account to, Transaction txn) {
            switch (request.getType()) {
                case DEBIT -> {
                    int updated = accountRepository.debitBalance(from.getId(), request.getAmount(), from.getVersion());
                    if (updated == 0) throw new ConcurrentModificationException(from.getId());
                }
                case CREDIT -> {
                    int updated = accountRepository.creditBalance(from.getId(), request.getAmount(), from.getVersion());
                    if (updated == 0) throw new ConcurrentModificationException(from.getId());
                }
                case TRANSFER -> {
                    int debited = accountRepository.debitBalance(from.getId(), request.getAmount(), from.getVersion());
                    if (debited == 0) throw new ConcurrentModificationException(from.getId());
                    int credited = accountRepository.creditBalance(to.getId(), request.getAmount(), to.getVersion());
                    if (credited == 0) {
                        // Compensating transaction - rollback debit
                        accountRepository.creditBalance(from.getId(), request.getAmount(), from.getVersion());
                        throw new ConcurrentModificationException(to.getId());
                    }
                }
                case REFUND -> {
                    int updated = accountRepository.creditBalance(from.getId(), request.getAmount(), from.getVersion());
                    if (updated == 0) throw new ConcurrentModificationException(from.getId());
                }
            }
        }

        @Retryable(retryFor = {OptimisticLockingFailureException.class}, maxAttempts = 3,
                backoff = @Backoff(delay = 200))
        @Transactional(isolation = Isolation.REPEATABLE_READ, timeout = 30)
        public TransactionResponse reverseTransaction(UUID transactionId) throws AccountNotFoundException {
            Transaction original = transactionRepository.findByIdWithLock(transactionId)
                    .orElseThrow(() -> new TransactionNotFoundException(transactionId.toString()));

            if (original.getStatus() != TransactionStatus.COMPLETED) {
                throw new InvalidTransactionStateException(
                        transactionId.toString(), original.getStatus().name(), "COMPLETED"
                );
            }

            Transaction reversal = Transaction.builder()
                    .referenceId("REV-" + UUID.randomUUID().toString().substring(0, 12).toUpperCase())
                    .accountId(original.getAccountId())
                    .toAccountId(original.getToAccountId())
                    .amount(original.getAmount())
                    .currency(original.getCurrency())
                    .type(TransactionType.REFUND)
                    .status(TransactionStatus.PROCESSING)
                    .description("Reversal of " + original.getReferenceId())
                    .build();

            reversal = transactionRepository.save(reversal);

            Account account = accountRepository.findByIdWithPessimisticLock(original.getAccountId())
                    .orElseThrow(() -> new AccountNotFoundException(original.getAccountId()));

            accountRepository.creditBalance(account.getId(), original.getAmount(), account.getVersion());

            original.setStatus(TransactionStatus.REVERSED);
            reversal.setStatus(TransactionStatus.COMPLETED);
            reversal.setProcessedAt(LocalDateTime.now());

            transactionRepository.save(original);
            return mapToResponse(transactionRepository.save(reversal));
        }


        @Transactional(readOnly = true)
        public TransactionResponse getTransaction(UUID id) {
            return transactionRepository.findById(id)
                    .map(this::mapToResponse)
                    .orElseThrow(() -> new TransactionNotFoundException(id.toString()));
        }

        @Transactional(readOnly = true)
        public PagedResponse<TransactionResponse> getAccountTransactions(
                String accountId, Pageable pageable) throws AccountNotFoundException {
            accountRepository.findById(accountId)
                    .orElseThrow(() -> new AccountNotFoundException(accountId));

            Page<Transaction> page = transactionRepository
                    .findByAccountIdOrderByCreatedAtDesc(accountId, pageable);

            return PagedResponse.<TransactionResponse>builder()
                    .content(page.getContent().stream().map(this::mapToResponse).toList())
                    .page(page.getNumber())
                    .size(page.getSize())
                    .totalElements(page.getTotalElements())
                    .totalPages(page.getTotalPages())
                    .last(page.isLast())
                    .build();
        }

        @Transactional//(readOnly = true)
        public AccountResponse getAccountBalance(String accountId) throws AccountNotFoundException {
            Account account = accountRepository.findByIdWithReadLock(accountId)
                    .orElseThrow(() -> new AccountNotFoundException(accountId));
            return mapAccountToResponse(account);
        }

        @Transactional(readOnly = true)
        public List<AccountResponse> getAllAccounts() {
            return accountRepository.findAllAccounts()
                    .stream()
                    .map(this::mapAccountToResponse)
                    .collect(Collectors.toList());
        }



        private TransactionResponse mapToResponse(Transaction t) {
            return TransactionResponse.builder()
                    .id(t.getId())
                    .referenceId(t.getReferenceId())
                    .accountId(t.getAccountId())
                    .toAccountId(t.getToAccountId())
                    .amount(t.getAmount())
                    .currency(t.getCurrency())
                    .type(t.getType())
                    .status(t.getStatus())
                    .description(t.getDescription())
                    .failureReason(t.getFailureReason())
                    .createdAt(t.getCreatedAt())
                    .processedAt(t.getProcessedAt())
                    .version(t.getVersion())
                    .build();
        }

        private AccountResponse mapAccountToResponse(Account a) {
            return AccountResponse.builder()
                    .id(a.getId())
                    .accountNumber(a.getAccountNumber())
                    .ownerName(a.getOwnerName())
                    .balance(a.getBalance())
                    .availableBalance(a.getAvailableBalance())
                    .currency(a.getCurrency())
                    .status(a.getStatus().name())
                    .build();
        }

}
