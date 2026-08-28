package com.example.bankingtransactionservice.dto;

import com.example.bankingtransactionservice.entity.Account;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;

/** Request and response payloads for account management. */
public final class AccountDtos {

    private AccountDtos() {
        // Container for nested record types.
    }

    /** Payload for opening a new account. */
    public record CreateAccountRequest(
            @NotBlank @Size(max = 64) String ownerUsername,
            @NotNull
                    @DecimalMin(value = "0.00", message = "opening balance cannot be negative")
                    @Digits(integer = 17, fraction = 2)
                    BigDecimal openingBalance,
            @NotBlank @Pattern(regexp = "^[A-Z]{3}$", message = "currency must be a 3-letter ISO code")
                    String currency) {}

    /** Payload for updating a mutable account attribute. */
    public record UpdateAccountRequest(@NotNull Boolean active) {}

    /** Account representation returned to clients. */
    public record AccountResponse(
            Long id,
            String accountNumber,
            String ownerUsername,
            BigDecimal balance,
            String currency,
            boolean active,
            Instant createdAt) {

        /** Maps the persistent entity onto its API representation. */
        public static AccountResponse from(Account account) {
            return new AccountResponse(
                    account.getId(),
                    account.getAccountNumber(),
                    account.getOwnerUsername(),
                    account.getBalance(),
                    account.getCurrency(),
                    account.isActive(),
                    account.getCreatedAt());
        }
    }
}
