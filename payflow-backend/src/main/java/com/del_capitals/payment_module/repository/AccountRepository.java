package com.del_capitals.payment_module.repository;


import com.del_capitals.payment_module.domain.Account;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Repository
public interface AccountRepository extends JpaRepository<Account, String> {

    @Query("SELECT a FROM Account a ORDER BY a.createdAt DESC")
    List<Account> findAllAccounts();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM Account a WHERE a.id = :id")
    Optional<Account> findByIdWithPessimisticLock(@Param("id") String id);

    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("SELECT a FROM Account a WHERE a.id = :id")
    Optional<Account> findByIdWithReadLock(@Param("id") String id);

    @Modifying
    @Query("UPDATE Account a SET a.balance = a.balance - :amount, " +
            "a.availableBalance = a.availableBalance - :amount, " +
            "a.dailySpent = a.dailySpent + :amount " +
            "WHERE a.id = :accountId AND a.balance >= :amount AND a.version = :version")
    int debitBalance(
            @Param("accountId") String accountId,
            @Param("amount") BigDecimal amount,
            @Param("version") Long version
    );

    @Modifying
    @Query("UPDATE Account a SET a.balance = a.balance + :amount, " +
            "a.availableBalance = a.availableBalance + :amount " +
            "WHERE a.id = :accountId AND a.version = :version")
    int creditBalance(
            @Param("accountId") String accountId,
            @Param("amount") BigDecimal amount,
            @Param("version") Long version
    );
}