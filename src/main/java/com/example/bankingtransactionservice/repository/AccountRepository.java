package com.example.bankingtransactionservice.repository;

import com.example.bankingtransactionservice.entity.Account;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Data access for {@link Account}. */
public interface AccountRepository extends JpaRepository<Account, Long> {

    Optional<Account> findByAccountNumber(String accountNumber);

    List<Account> findByOwnerUsername(String ownerUsername);

    boolean existsByAccountNumber(String accountNumber);

    /**
     * Loads an account with a pessimistic write lock.
     *
     * <p>Used by the transfer path so two concurrent transfers against the same account serialize
     * at the database rather than racing and losing an update.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Account a where a.accountNumber = :accountNumber")
    Optional<Account> findByAccountNumberForUpdate(@Param("accountNumber") String accountNumber);
}
