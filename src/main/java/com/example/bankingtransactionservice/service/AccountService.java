package com.example.bankingtransactionservice.service;

import com.example.bankingtransactionservice.entity.Account;
import com.example.bankingtransactionservice.entity.Role;
import com.example.bankingtransactionservice.repository.AccountRepository;
import java.math.BigDecimal;
import java.security.SecureRandom;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Account lifecycle operations and ownership checks. */
@Service
public class AccountService {

    private static final int ACCOUNT_NUMBER_DIGITS = 12;
    private static final int MAX_GENERATION_ATTEMPTS = 10;

    private final AccountRepository accountRepository;
    private final SecureRandom random = new SecureRandom();

    public AccountService(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    /** Opens a new account with a generated, unique account number. */
    @Transactional
    public Account create(String ownerUsername, BigDecimal openingBalance, String currency) {
        if (openingBalance.signum() < 0) {
            throw BankingException.businessRule("Opening balance cannot be negative");
        }
        String accountNumber = generateUniqueAccountNumber();
        Account account =
                new Account(accountNumber, ownerUsername, openingBalance.setScale(2), currency);
        return accountRepository.save(account);
    }

    /** Returns a single account by its account number. */
    @Transactional(readOnly = true)
    public Account findByAccountNumber(String accountNumber) {
        return accountRepository
                .findByAccountNumber(accountNumber)
                .orElseThrow(() -> BankingException.notFound("Account not found: " + accountNumber));
    }

    /** Returns every account in the system. */
    @Transactional(readOnly = true)
    public List<Account> findAll() {
        return accountRepository.findAll();
    }

    /** Returns the accounts owned by a given username. */
    @Transactional(readOnly = true)
    public List<Account> findByOwner(String ownerUsername) {
        return accountRepository.findByOwnerUsername(ownerUsername);
    }

    /** Activates or deactivates an account. */
    @Transactional
    public Account setActive(String accountNumber, boolean active) {
        Account account = findByAccountNumber(accountNumber);
        account.setActive(active);
        return accountRepository.save(account);
    }

    /**
     * Closes an account.
     *
     * <p>Accounts holding a non-zero balance cannot be closed — the funds must be moved first.
     */
    @Transactional
    public void close(String accountNumber) {
        Account account = findByAccountNumber(accountNumber);
        if (account.getBalance().compareTo(BigDecimal.ZERO) != 0) {
            throw BankingException.conflict(
                    "Cannot close account " + accountNumber + " while it holds a balance");
        }
        accountRepository.delete(account);
    }

    /**
     * Authorizes access to an account.
     *
     * <p>ADMIN and TELLER may act on any account; CUSTOMER only on accounts they own.
     */
    public void authorizeAccess(Account account, String username, Role role) {
        if (role == Role.CUSTOMER && !account.getOwnerUsername().equals(username)) {
            throw BankingException.forbidden("You may only access your own accounts");
        }
    }

    private String generateUniqueAccountNumber() {
        for (int attempt = 0; attempt < MAX_GENERATION_ATTEMPTS; attempt++) {
            StringBuilder sb = new StringBuilder("ACC");
            for (int i = 0; i < ACCOUNT_NUMBER_DIGITS; i++) {
                sb.append(random.nextInt(10));
            }
            String candidate = sb.toString();
            if (!accountRepository.existsByAccountNumber(candidate)) {
                return candidate;
            }
        }
        throw BankingException.conflict("Could not allocate a unique account number");
    }
}
