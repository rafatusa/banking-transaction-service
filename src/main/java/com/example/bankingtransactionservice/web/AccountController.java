package com.example.bankingtransactionservice.web;

import com.example.bankingtransactionservice.dto.AccountDtos;
import com.example.bankingtransactionservice.entity.Account;
import com.example.bankingtransactionservice.entity.Role;
import com.example.bankingtransactionservice.service.AccountService;
import com.example.bankingtransactionservice.service.AuditService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Account CRUD endpoints. */
@RestController
@RequestMapping("/api/accounts")
@Tag(name = "Accounts", description = "Open, inspect, update and close bank accounts")
@SecurityRequirement(name = "bearerAuth")
public class AccountController extends ApiControllerSupport {

    private final AccountService accountService;
    private final AuditService auditService;

    public AccountController(AccountService accountService, AuditService auditService) {
        this.accountService = accountService;
        this.auditService = auditService;
    }

    /** Opens a new account. Tellers and admins only. */
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','TELLER')")
    @Operation(summary = "Open an account")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Account opened"),
        @ApiResponse(responseCode = "400", description = "Validation failed"),
        @ApiResponse(responseCode = "403", description = "Caller lacks the required role")
    })
    public ResponseEntity<AccountDtos.AccountResponse> create(
            @Valid @RequestBody AccountDtos.CreateAccountRequest request,
            Authentication authentication,
            HttpServletRequest httpRequest) {

        Account account =
                accountService.create(
                        request.ownerUsername(), request.openingBalance(), request.currency());

        auditService.record(
                authentication.getName(),
                "CREATE_ACCOUNT",
                "Account",
                account.getAccountNumber(),
                "SUCCESS",
                "Opened account for " + request.ownerUsername(),
                clientIp(httpRequest));

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(AccountDtos.AccountResponse.from(account));
    }

    /**
     * Lists accounts.
     *
     * <p>Admins and tellers see every account; customers see only their own.
     */
    @GetMapping
    @Operation(summary = "List accounts visible to the caller")
    public List<AccountDtos.AccountResponse> list(Authentication authentication) {
        Role role = currentRole(authentication);
        List<Account> accounts =
                (role == Role.CUSTOMER)
                        ? accountService.findByOwner(authentication.getName())
                        : accountService.findAll();
        return accounts.stream().map(AccountDtos.AccountResponse::from).toList();
    }

    /** Returns a single account. */
    @GetMapping("/{accountNumber}")
    @Operation(summary = "Get one account")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Account found"),
        @ApiResponse(responseCode = "403", description = "Not the caller's account"),
        @ApiResponse(responseCode = "404", description = "No such account")
    })
    public AccountDtos.AccountResponse get(
            @PathVariable String accountNumber, Authentication authentication) {
        Account account = accountService.findByAccountNumber(accountNumber);
        accountService.authorizeAccess(account, authentication.getName(), currentRole(authentication));
        return AccountDtos.AccountResponse.from(account);
    }

    /** Activates or deactivates an account. Tellers and admins only. */
    @PatchMapping("/{accountNumber}")
    @PreAuthorize("hasAnyRole('ADMIN','TELLER')")
    @Operation(summary = "Activate or deactivate an account")
    public AccountDtos.AccountResponse update(
            @PathVariable String accountNumber,
            @Valid @RequestBody AccountDtos.UpdateAccountRequest request,
            Authentication authentication,
            HttpServletRequest httpRequest) {

        Account account = accountService.setActive(accountNumber, request.active());

        auditService.record(
                authentication.getName(),
                "UPDATE_ACCOUNT",
                "Account",
                accountNumber,
                "SUCCESS",
                "Set active=" + request.active(),
                clientIp(httpRequest));

        return AccountDtos.AccountResponse.from(account);
    }

    /** Closes a zero-balance account. Admins only. */
    @DeleteMapping("/{accountNumber}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Close an account")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Account closed"),
        @ApiResponse(responseCode = "409", description = "Account still holds a balance")
    })
    public ResponseEntity<Void> close(
            @PathVariable String accountNumber,
            Authentication authentication,
            HttpServletRequest httpRequest) {

        accountService.close(accountNumber);

        auditService.record(
                authentication.getName(),
                "CLOSE_ACCOUNT",
                "Account",
                accountNumber,
                "SUCCESS",
                "Account closed",
                clientIp(httpRequest));

        return ResponseEntity.noContent().build();
    }
}
