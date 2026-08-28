package com.example.bankingtransactionservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.bankingtransactionservice.entity.Account;
import com.example.bankingtransactionservice.entity.Role;
import com.example.bankingtransactionservice.entity.TransactionRecord;
import com.example.bankingtransactionservice.repository.AccountRepository;
import com.example.bankingtransactionservice.repository.TransactionRecordRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

/** Unit tests for the money-movement rules. */
@ExtendWith(MockitoExtension.class)
class TransferServiceTest {

    private static final String SOURCE = "ACC000000000001";
    private static final String TARGET = "ACC000000000002";
    private static final String IP = "203.0.113.10";

    @Mock private AccountRepository accountRepository;
    @Mock private TransactionRecordRepository transactionRepository;
    @Mock private AuditService auditService;

    @InjectMocks private TransferService transferService;

    private Account source;
    private Account target;

    @BeforeEach
    void setUp() {
        source = new Account(SOURCE, "alice", new BigDecimal("500.00"), "USD");
        target = new Account(TARGET, "bob", new BigDecimal("100.00"), "USD");
    }

    private void lockBoth() {
        when(accountRepository.findByAccountNumberForUpdate(SOURCE)).thenReturn(Optional.of(source));
        when(accountRepository.findByAccountNumberForUpdate(TARGET)).thenReturn(Optional.of(target));
    }

    private TransferService.TransferCommand command(
            String from, String to, String amount, String actor, Role role) {
        return new TransferService.TransferCommand(
                from, to, new BigDecimal(amount), "test transfer", actor, role, IP);
    }

    @Test
    @DisplayName("moves funds and records a COMPLETED transaction")
    void transferSucceeds() {
        lockBoth();
        when(transactionRepository.save(any(TransactionRecord.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        TransactionRecord record =
                transferService.transfer(
                        command(SOURCE, TARGET, "125.50", "alice", Role.CUSTOMER));

        assertThat(source.getBalance()).isEqualByComparingTo("374.50");
        assertThat(target.getBalance()).isEqualByComparingTo("225.50");
        assertThat(record.getStatus()).isEqualTo(TransactionRecord.Status.COMPLETED);
        assertThat(record.getReference()).isNotBlank();
        assertThat(record.getInitiatedBy()).isEqualTo("alice");
        verify(auditService)
                .record(
                        eq("alice"),
                        eq("TRANSFER"),
                        eq("Account"),
                        eq(SOURCE),
                        eq("SUCCESS"),
                        anyString(),
                        eq(IP));
    }

    @Test
    @DisplayName("rejects a transfer that would overdraw the source account")
    void rejectsInsufficientFunds() {
        lockBoth();

        assertThatThrownBy(
                        () ->
                                transferService.transfer(
                                        command(SOURCE, TARGET, "10000.00", "alice", Role.CUSTOMER)))
                .isInstanceOf(BankingException.class)
                .hasMessageContaining("Insufficient funds");

        assertThat(source.getBalance()).isEqualByComparingTo("500.00");
        assertThat(target.getBalance()).isEqualByComparingTo("100.00");
        verify(transactionRepository, never()).save(any());
        verify(auditService)
                .record(
                        anyString(),
                        anyString(),
                        anyString(),
                        anyString(),
                        eq("REJECTED"),
                        anyString(),
                        anyString());
    }

    @Test
    @DisplayName("rejects a transfer to the same account")
    void rejectsSelfTransfer() {
        assertThatThrownBy(
                        () ->
                                transferService.transfer(
                                        command(SOURCE, SOURCE, "10.00", "alice", Role.CUSTOMER)))
                .isInstanceOf(BankingException.class)
                .hasMessageContaining("must differ");

        verify(accountRepository, never()).findByAccountNumberForUpdate(anyString());
    }

    @Test
    @DisplayName("rejects a non-positive amount")
    void rejectsNonPositiveAmount() {
        assertThatThrownBy(
                        () ->
                                transferService.transfer(
                                        command(SOURCE, TARGET, "0.00", "alice", Role.CUSTOMER)))
                .isInstanceOf(BankingException.class)
                .hasMessageContaining("must be positive");

        assertThatThrownBy(
                        () ->
                                transferService.transfer(
                                        command(SOURCE, TARGET, "-5.00", "alice", Role.CUSTOMER)))
                .isInstanceOf(BankingException.class)
                .hasMessageContaining("must be positive");
    }

    @Test
    @DisplayName("forbids a customer transferring from an account they do not own")
    void forbidsCustomerTransferringFromForeignAccount() {
        lockBoth();

        assertThatThrownBy(
                        () ->
                                transferService.transfer(
                                        command(SOURCE, TARGET, "10.00", "mallory", Role.CUSTOMER)))
                .isInstanceOf(BankingException.class)
                .hasMessageContaining("your own accounts");

        verify(transactionRepository, never()).save(any());
    }

    @Test
    @DisplayName("allows a teller to transfer from an account they do not own")
    void allowsTellerTransferringFromAnyAccount() {
        lockBoth();
        when(transactionRepository.save(any(TransactionRecord.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        TransactionRecord record =
                transferService.transfer(command(SOURCE, TARGET, "50.00", "teller1", Role.TELLER));

        assertThat(record.getStatus()).isEqualTo(TransactionRecord.Status.COMPLETED);
        assertThat(source.getBalance()).isEqualByComparingTo("450.00");
    }

    @Test
    @DisplayName("rejects a transfer involving an inactive account")
    void rejectsInactiveAccount() {
        target.setActive(false);
        lockBoth();

        assertThatThrownBy(
                        () ->
                                transferService.transfer(
                                        command(SOURCE, TARGET, "10.00", "alice", Role.CUSTOMER)))
                .isInstanceOf(BankingException.class)
                .hasMessageContaining("must be active");
    }

    @Test
    @DisplayName("rejects a cross-currency transfer")
    void rejectsCurrencyMismatch() {
        target.setCurrency("EUR");
        lockBoth();

        assertThatThrownBy(
                        () ->
                                transferService.transfer(
                                        command(SOURCE, TARGET, "10.00", "alice", Role.CUSTOMER)))
                .isInstanceOf(BankingException.class)
                .hasMessageContaining("Cross-currency");
    }

    @Test
    @DisplayName("fails when the source account does not exist")
    void failsWhenSourceMissing() {
        when(accountRepository.findByAccountNumberForUpdate(SOURCE)).thenReturn(Optional.empty());

        assertThatThrownBy(
                        () ->
                                transferService.transfer(
                                        command(SOURCE, TARGET, "10.00", "alice", Role.CUSTOMER)))
                .isInstanceOf(BankingException.class)
                .hasMessageContaining("Account not found");
    }

    @Test
    @DisplayName("locks accounts in account-number order regardless of transfer direction")
    void locksInDeterministicOrder() {
        lockBoth();
        when(transactionRepository.save(any(TransactionRecord.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // Transfer in the reverse direction: TARGET -> SOURCE.
        target.credit(new BigDecimal("1000.00"));
        transferService.transfer(command(TARGET, SOURCE, "10.00", "bob", Role.CUSTOMER));

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(accountRepository, atLeast(2)).findByAccountNumberForUpdate(captor.capture());

        // The lower account number is always locked first, preventing deadlock.
        assertThat(captor.getAllValues().get(0)).isEqualTo(SOURCE);
    }

    @Test
    @DisplayName("returns an empty page when a customer owns no accounts")
    void emptyHistoryForNoAccounts() {
        Pageable pageable = PageRequest.of(0, 20);
        Page<TransactionRecord> page = transferService.historyForAccounts(List.of(), pageable);
        assertThat(page.getTotalElements()).isZero();
        verify(transactionRepository, never()).findByAccounts(any(), any());
    }

    @Test
    @DisplayName("delegates single-account history to the repository")
    void delegatesAccountHistory() {
        Pageable pageable = PageRequest.of(0, 20);
        when(transactionRepository.findByAccount(SOURCE, pageable)).thenReturn(Page.empty(pageable));

        transferService.historyForAccount(SOURCE, pageable);

        verify(transactionRepository).findByAccount(SOURCE, pageable);
    }

    @Test
    @DisplayName("delegates multi-account history to the repository")
    void delegatesMultiAccountHistory() {
        Pageable pageable = PageRequest.of(0, 20);
        List<String> accounts = List.of(SOURCE, TARGET);
        when(transactionRepository.findByAccounts(accounts, pageable))
                .thenReturn(Page.empty(pageable));

        transferService.historyForAccounts(accounts, pageable);

        verify(transactionRepository).findByAccounts(accounts, pageable);
    }
}
