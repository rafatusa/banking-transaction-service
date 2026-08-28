package com.example.bankingtransactionservice.repository;

import com.example.bankingtransactionservice.entity.TransactionRecord;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Data access for {@link TransactionRecord}. */
public interface TransactionRecordRepository extends JpaRepository<TransactionRecord, Long> {

    @Query(
            "select t from TransactionRecord t "
                    + "where t.sourceAccount = :accountNumber or t.targetAccount = :accountNumber "
                    + "order by t.createdAt desc")
    Page<TransactionRecord> findByAccount(
            @Param("accountNumber") String accountNumber, Pageable pageable);

    @Query(
            "select t from TransactionRecord t "
                    + "where t.sourceAccount in :accountNumbers or t.targetAccount in :accountNumbers "
                    + "order by t.createdAt desc")
    Page<TransactionRecord> findByAccounts(
            @Param("accountNumbers") java.util.Collection<String> accountNumbers, Pageable pageable);
}
