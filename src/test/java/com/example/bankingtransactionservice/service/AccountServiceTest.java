package com.example.bankingtransactionservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.bankingtransactionservice.entity.Account;
import com.example.bankingtransactionservice.entity.Role;
import com.example.bankingtransactionservice.repository.AccountRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Unit tests for account lifecycle and ownership rules. */
@ExtendWith(MockitoExtension.class)
class AccountServiceTest {

    @Mock private AccountRepository accountRepository;

    @InjectMocks private AccountService accountService;

    @Test
    @DisplayName("opens an account with a generated unique number")
    void createsAccount() {
        when(accountRepository.existsByAccountNumber(anyString())).thenReturn(false);
        when(accountRepository.save(any(Account.class))).thenAnswer(i -> i.getArgument(0));

        Account account = accountService.create("alice", new BigDecimal("250"), "USD");

        assertThat(account.getOwnerUsername()).isEqualTo("alice");
        assertThat(account.getBalance()).isEqualByComparingTo("250.00");
        assertThat(account.getCurrency()).isEqualTo("USD");
        assertThat(account.getAccountNumber()).startsWith("ACC").hasSize(15);
    }

    @Test
    @DisplayName("retries number generation when the first candidate collides")
    void retriesOnAccountNumberCollision() {
        when(accountRepository.existsByAccountNumber(anyString()))
                .thenReturn(true)
                .thenReturn(false);
        when(accountRepository.save(any(Account.class))).thenAnswer(i -> i.getArgument(0));

        Account account = accountService.create("alice", BigDecimal.ZERO, "USD");

        assertThat(account.getAccountNumber()).isNotBlank();
        verify(accountRepository, org.mockito.Mockito.times(2)).existsByAccountNumber(anyString());
    }

    @Test
    @DisplayName("refuses a negative opening balance")
    void rejectsNegativeOpeningBalance() {
        assertThatThrownBy(() -> accountService.create("alice", new BigDecimal("-1"), "USD"))
                .isInstanceOf(BankingException.class)
                .hasMessageContaining("cannot be negative");

        verify(accountRepository, never()).save(any());
    }

    @Test
    @DisplayName("gives up after exhausting account-number generation attempts")
    void failsWhenNumberSpaceExhausted() {
        when(accountRepository.existsByAccountNumber(anyString())).thenReturn(true);

        assertThatThrownBy(() -> accountService.create("alice", BigDecimal.ZERO, "USD"))
                .isInstanceOf(BankingException.class)
                .hasMessageContaining("unique account number");
    }

    @Test
    @DisplayName("raises NOT_FOUND for an unknown account number")
    void notFoundForUnknownAccount() {
        when(accountRepository.findByAccountNumber("nope")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> accountService.findByAccountNumber("nope"))
                .isInstanceOf(BankingException.class)
                .hasMessageContaining("Account not found");
    }

    @Test
    @DisplayName("lists all accounts and accounts by owner")
    void listsAccounts() {
        Account a = new Account("ACC1", "alice", BigDecimal.TEN, "USD");
        when(accountRepository.findAll()).thenReturn(List.of(a));
        when(accountRepository.findByOwnerUsername("alice")).thenReturn(List.of(a));

        assertThat(accountService.findAll()).containsExactly(a);
        assertThat(accountService.findByOwner("alice")).containsExactly(a);
    }

    @Test
    @DisplayName("activates and deactivates an account")
    void togglesActive() {
        Account account = new Account("ACC1", "alice", BigDecimal.TEN, "USD");
        when(accountRepository.findByAccountNumber("ACC1")).thenReturn(Optional.of(account));
        when(accountRepository.save(any(Account.class))).thenAnswer(i -> i.getArgument(0));

        assertThat(accountService.setActive("ACC1", false).isActive()).isFalse();
        assertThat(accountService.setActive("ACC1", true).isActive()).isTrue();
    }

    @Test
    @DisplayName("closes a zero-balance account")
    void closesEmptyAccount() {
        Account account = new Account("ACC1", "alice", BigDecimal.ZERO, "USD");
        when(accountRepository.findByAccountNumber("ACC1")).thenReturn(Optional.of(account));

        accountService.close("ACC1");

        verify(accountRepository).delete(account);
    }

    @Test
    @DisplayName("refuses to close an account that still holds funds")
    void refusesToCloseFundedAccount() {
        Account account = new Account("ACC1", "alice", new BigDecimal("0.01"), "USD");
        when(accountRepository.findByAccountNumber("ACC1")).thenReturn(Optional.of(account));

        assertThatThrownBy(() -> accountService.close("ACC1"))
                .isInstanceOf(BankingException.class)
                .hasMessageContaining("holds a balance");

        verify(accountRepository, never()).delete(any());
    }

    @Test
    @DisplayName("lets a customer reach only their own account")
    void customerOwnershipEnforced() {
        Account account = new Account("ACC1", "alice", BigDecimal.TEN, "USD");

        assertThatCode(() -> accountService.authorizeAccess(account, "alice", Role.CUSTOMER))
                .doesNotThrowAnyException();

        assertThatThrownBy(() -> accountService.authorizeAccess(account, "mallory", Role.CUSTOMER))
                .isInstanceOf(BankingException.class)
                .hasMessageContaining("your own accounts");
    }

    @Test
    @DisplayName("lets staff reach any account")
    void staffBypassOwnership() {
        Account account = new Account("ACC1", "alice", BigDecimal.TEN, "USD");

        assertThatCode(() -> accountService.authorizeAccess(account, "teller1", Role.TELLER))
                .doesNotThrowAnyException();
        assertThatCode(() -> accountService.authorizeAccess(account, "admin", Role.ADMIN))
                .doesNotThrowAnyException();
    }
}
