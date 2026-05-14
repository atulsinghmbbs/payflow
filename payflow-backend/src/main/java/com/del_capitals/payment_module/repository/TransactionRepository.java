package com.del_capitals.payment_module.repository;


import com.del_capitals.payment_module.domain.Transaction;
import com.del_capitals.payment_module.enumeration.TransactionStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, UUID> {

    Optional<Transaction> findByIdempotencyKey(String idempotencyKey);


    Page<Transaction> findByAccountIdOrderByCreatedAtDesc(String accountId, Pageable pageable);


    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM Transaction t WHERE t.id = :id")
    Optional<Transaction> findByIdWithLock(@Param("id") UUID id);

    @Query("SELECT COALESCE(SUM(t.amount), 0) FROM Transaction t " +
            "WHERE t.accountId = :accountId AND t.type IN ('DEBIT','TRANSFER') " +
            "AND t.status = 'COMPLETED' AND t.createdAt >= :since")
    BigDecimal sumDailyDebits(
            @Param("accountId") String accountId,
            @Param("since") LocalDateTime since
    );


    @Query("SELECT COUNT(t) FROM Transaction t WHERE t.accountId = :accountId " +
            "AND t.createdAt >= :since")
    long countRecentTransactions(
            @Param("accountId") String accountId,
            @Param("since") LocalDateTime since
    );

}