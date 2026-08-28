package com.example.bankingtransactionservice.dto;

import com.example.bankingtransactionservice.entity.TransactionRecord;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;

/** Request and response payloads for money movement. */
public final class TransferDtos {

    private TransferDtos() {
        // Container for nested record types.
    }

    /** Instruction to move funds between two accounts. */
    public record TransferRequest(
            @NotBlank @Size(max = 24) String sourceAccount,
            @NotBlank @Size(max = 24) String targetAccount,
            @NotNull
                    @DecimalMin(value = "0.01", message = "transfer amount must be positive")
                    @Digits(integer = 17, fraction = 2)
                    BigDecimal amount,
            @Size(max = 255) String description) {}

    /** The persisted outcome of a transfer. */
    public record TransactionResponse(
            Long id,
            String reference,
            String sourceAccount,
            String targetAccount,
            BigDecimal amount,
            String currency,
            String status,
            String description,
            String initiatedBy,
            Instant createdAt) {

        /** Maps the persistent entity onto its API representation. */
        public static TransactionResponse from(TransactionRecord record) {
            return new TransactionResponse(
                    record.getId(),
                    record.getReference(),
                    record.getSourceAccount(),
                    record.getTargetAccount(),
                    record.getAmount(),
                    record.getCurrency(),
                    record.getStatus().name(),
                    record.getDescription(),
                    record.getInitiatedBy(),
                    record.getCreatedAt());
        }
    }
}
