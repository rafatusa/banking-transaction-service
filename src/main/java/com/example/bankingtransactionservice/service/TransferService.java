package com.example.bankingtransactionservice.service;

import com.example.bankingtransactionservice.entity.Account;
import com.example.bankingtransactionservice.entity.Role;
import com.example.bankingtransactionservice.entity.TransactionRecord;
import com.example.bankingtransactionservice.repository.AccountRepository;
import com.example.bankingtransactionservice.repository.TransactionRecordRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Executes money transfers atomically and records their outcome. */
@Service
public class TransferService {

    private final AccountRepository accountRepository;
    private final TransactionRecordRepository transactionRepository;
    private final AuditService auditService;

    public TransferService(
            AccountRepository accountRepository,
            TransactionRecordRepository transactionRepository,
            AuditService auditService) {
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
        this.auditService = auditService;
    }

    /**
     * Moves funds between two accounts.
     *
     * <p>The whole method is one transaction: either both the debit and the credit are persisted
     * along with the transaction record, or nothing is.
     *
     * <p>A rejected transfer still produces an audit entry — {@link AuditService} writes in its own
     * transaction so the record survives this one rolling back.
     *
     * @throws BankingException if validation, ownership or funds checks fail
     */
    @Transactional
    public TransactionRecord transfer(TransferCommand command) {
        validateRequest(command);

        LockedPair accounts = lockPair(command.sourceAccount(), command.targetAccount());
        Account source = accounts.source();
        Account target = accounts.target();

        BigDecimal amount = command.amount().setScale(2);
        validateAccounts(command, source, target, amount);

        source.debit(amount);
        target.credit(amount);
        accountRepository.save(source);
        accountRepository.save(target);

        TransactionRecord record =
                transactionRepository.save(
                        new TransactionRecord(
                                UUID.randomUUID().toString(),
                                command.sourceAccount(),
                                command.targetAccount(),
                                amount,
                                source.getCurrency(),
                                TransactionRecord.Status.COMPLETED,
                                command.description(),
                                command.initiatedBy()));

        auditService.record(
                command.initiatedBy(),
                "TRANSFER",
                "Account",
                command.sourceAccount(),
                "SUCCESS",
                "Transferred "
                        + amount
                        + " "
                        + source.getCurrency()
                        + " to "
                        + command.targetAccount(),
                command.sourceIp());

        return record;
    }

    /** Checks that can be made without touching the database. */
    private void validateRequest(TransferCommand command) {
        if (command.sourceAccount().equals(command.targetAccount())) {
            reject(command, command.amount(), "source and target are the same account");
            throw BankingException.businessRule("Source and target accounts must differ");
        }
        if (command.amount().signum() <= 0) {
            reject(command, command.amount(), "non-positive amount");
            throw BankingException.businessRule("Transfer amount must be positive");
        }
    }

    /** Checks that require the locked account rows. */
    private void validateAccounts(
            TransferCommand command, Account source, Account target, BigDecimal amount) {

        if (command.initiatorRole() == Role.CUSTOMER
                && !source.getOwnerUsername().equals(command.initiatedBy())) {
            reject(command, amount, "caller does not own the source account");
            throw BankingException.forbidden("You may only transfer from your own accounts");
        }
        if (!source.isActive() || !target.isActive()) {
            reject(command, amount, "an involved account is inactive");
            throw BankingException.businessRule("Both accounts must be active");
        }
        if (!source.getCurrency().equals(target.getCurrency())) {
            reject(command, amount, "currency mismatch");
            throw BankingException.businessRule(
                    "Cross-currency transfers are not supported: "
                            + source.getCurrency()
                            + " -> "
                            + target.getCurrency());
        }
        if (!source.hasSufficientFunds(amount)) {
            reject(command, amount, "insufficient funds");
            throw BankingException.businessRule(
                    "Insufficient funds in account " + command.sourceAccount());
        }
    }

    /**
     * Locks both accounts in a deterministic order.
     *
     * <p>Ordering by account number is what prevents two simultaneous transfers in opposite
     * directions from grabbing the same two rows in opposite sequence and deadlocking.
     */
    private LockedPair lockPair(String sourceAccountNumber, String targetAccountNumber) {
        Account first;
        Account second;
        if (sourceAccountNumber.compareTo(targetAccountNumber) < 0) {
            first = lock(sourceAccountNumber);
            second = lock(targetAccountNumber);
        } else {
            second = lock(targetAccountNumber);
            first = lock(sourceAccountNumber);
        }

        boolean firstIsSource = first.getAccountNumber().equals(sourceAccountNumber);
        return new LockedPair(
                firstIsSource ? first : second, firstIsSource ? second : first);
    }

    /** Returns the transaction history for a single account, most recent first. */
    @Transactional(readOnly = true)
    public Page<TransactionRecord> historyForAccount(String accountNumber, Pageable pageable) {
        return transactionRepository.findByAccount(accountNumber, pageable);
    }

    /** Returns the transaction history spanning several accounts, most recent first. */
    @Transactional(readOnly = true)
    public Page<TransactionRecord> historyForAccounts(
            List<String> accountNumbers, Pageable pageable) {
        if (accountNumbers.isEmpty()) {
            return Page.empty(pageable);
        }
        return transactionRepository.findByAccounts(accountNumbers, pageable);
    }

    private Account lock(String accountNumber) {
        return accountRepository
                .findByAccountNumberForUpdate(accountNumber)
                .orElseThrow(() -> BankingException.notFound("Account not found: " + accountNumber));
    }

    private void reject(TransferCommand command, BigDecimal amount, String reason) {
        auditService.record(
                command.initiatedBy(),
                "TRANSFER",
                "Account",
                command.sourceAccount(),
                "REJECTED",
                "Rejected transfer of "
                        + amount
                        + " to "
                        + command.targetAccount()
                        + ": "
                        + reason,
                command.sourceIp());
    }

    /** The two accounts of a transfer, already locked and identified by role. */
    private record LockedPair(Account source, Account target) {}

    /**
     * A transfer instruction.
     *
     * <p>Grouping the parameters keeps the service signature stable and readable; the seven
     * separate arguments it replaces were both hard to call correctly and easy to transpose.
     */
    public record TransferCommand(
            String sourceAccount,
            String targetAccount,
            BigDecimal amount,
            String description,
            String initiatedBy,
            Role initiatorRole,
            String sourceIp) {}
}
