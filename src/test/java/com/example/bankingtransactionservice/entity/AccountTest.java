package com.example.bankingtransactionservice.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Unit tests for the balance arithmetic on {@link Account}. */
class AccountTest {

    private Account account(String balance) {
        return new Account("ACC1", "alice", new BigDecimal(balance), "USD");
    }

    @Test
    @DisplayName("credit increases the balance exactly")
    void creditAddsFunds() {
        Account a = account("100.00");
        a.credit(new BigDecimal("0.55"));
        assertThat(a.getBalance()).isEqualByComparingTo("100.55");
    }

    @Test
    @DisplayName("debit reduces the balance exactly")
    void debitRemovesFunds() {
        Account a = account("100.00");
        a.debit(new BigDecimal("40.25"));
        assertThat(a.getBalance()).isEqualByComparingTo("59.75");
    }

    @Test
    @DisplayName("debit of the entire balance is permitted")
    void debitToZeroAllowed() {
        Account a = account("50.00");
        a.debit(new BigDecimal("50.00"));
        assertThat(a.getBalance()).isEqualByComparingTo("0.00");
    }

    @Test
    @DisplayName("debit beyond the balance is refused")
    void debitBeyondBalanceRejected() {
        Account a = account("50.00");

        assertThatThrownBy(() -> a.debit(new BigDecimal("50.01")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Insufficient funds");

        assertThat(a.getBalance()).isEqualByComparingTo("50.00");
    }

    @Test
    @DisplayName("sufficiency check matches the debit rule at the boundary")
    void sufficiencyCheckBoundary() {
        Account a = account("50.00");

        assertThat(a.hasSufficientFunds(new BigDecimal("49.99"))).isTrue();
        assertThat(a.hasSufficientFunds(new BigDecimal("50.00"))).isTrue();
        assertThat(a.hasSufficientFunds(new BigDecimal("50.01"))).isFalse();
    }

    @Test
    @DisplayName("new accounts are active and carry their opening values")
    void constructorDefaults() {
        Account a = account("10.00");

        assertThat(a.isActive()).isTrue();
        assertThat(a.getAccountNumber()).isEqualTo("ACC1");
        assertThat(a.getOwnerUsername()).isEqualTo("alice");
        assertThat(a.getCurrency()).isEqualTo("USD");
        assertThat(a.getCreatedAt()).isNotNull();
        assertThat(a.getVersion()).isZero();
    }
}
