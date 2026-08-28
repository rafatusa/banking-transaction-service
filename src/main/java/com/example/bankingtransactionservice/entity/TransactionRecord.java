package com.example.bankingtransactionservice.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;

/** An immutable record of a completed or rejected money movement. */
@Entity
@Table(
        name = "transaction_record",
        indexes = {
            @Index(name = "idx_txn_source", columnList = "source_account"),
            @Index(name = "idx_txn_target", columnList = "target_account"),
            @Index(name = "idx_txn_created", columnList = "created_at")
        })
public class TransactionRecord {

    /** Outcome of a transfer attempt. */
    public enum Status {
        COMPLETED,
        REJECTED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "reference", nullable = false, unique = true, length = 36)
    private String reference;

    @Column(name = "source_account", nullable = false, length = 24)
    private String sourceAccount;

    @Column(name = "target_account", nullable = false, length = 24)
    private String targetAccount;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Status status;

    @Column(length = 255)
    private String description;

    @Column(name = "initiated_by", nullable = false, length = 64)
    private String initiatedBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected TransactionRecord() {
        // JPA
    }

    public TransactionRecord(
            String reference,
            String sourceAccount,
            String targetAccount,
            BigDecimal amount,
            String currency,
            Status status,
            String description,
            String initiatedBy) {
        this.reference = reference;
        this.sourceAccount = sourceAccount;
        this.targetAccount = targetAccount;
        this.amount = amount;
        this.currency = currency;
        this.status = status;
        this.description = description;
        this.initiatedBy = initiatedBy;
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getReference() {
        return reference;
    }

    public String getSourceAccount() {
        return sourceAccount;
    }

    public String getTargetAccount() {
        return targetAccount;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getCurrency() {
        return currency;
    }

    public Status getStatus() {
        return status;
    }

    public String getDescription() {
        return description;
    }

    public String getInitiatedBy() {
        return initiatedBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
