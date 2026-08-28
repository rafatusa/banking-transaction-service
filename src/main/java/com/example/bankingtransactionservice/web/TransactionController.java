package com.example.bankingtransactionservice.web;

import com.example.bankingtransactionservice.dto.TransferDtos;
import com.example.bankingtransactionservice.entity.Account;
import com.example.bankingtransactionservice.entity.Role;
import com.example.bankingtransactionservice.service.AccountService;
import com.example.bankingtransactionservice.service.TransferService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Transaction history endpoints. */
@RestController
@RequestMapping("/api/transactions")
@Tag(name = "Transactions", description = "Read the transaction history")
@SecurityRequirement(name = "bearerAuth")
public class TransactionController extends ApiControllerSupport {

    private static final int MAX_PAGE_SIZE = 200;

    private final TransferService transferService;
    private final AccountService accountService;

    public TransactionController(TransferService transferService, AccountService accountService) {
        this.transferService = transferService;
        this.accountService = accountService;
    }

    /**
     * Returns transaction history.
     *
     * <p>With {@code accountNumber} the history is scoped to that account (subject to ownership);
     * without it, customers see the history of all accounts they own and staff see everything.
     */
    @GetMapping
    @Operation(summary = "List transactions visible to the caller")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "History returned"),
        @ApiResponse(responseCode = "403", description = "Not the caller's account"),
        @ApiResponse(responseCode = "404", description = "No such account")
    })
    public Page<TransferDtos.TransactionResponse> history(
            @RequestParam(required = false) String accountNumber,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Authentication authentication) {

        Pageable pageable =
                PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE));
        Role role = currentRole(authentication);

        if (accountNumber != null && !accountNumber.isBlank()) {
            Account account = accountService.findByAccountNumber(accountNumber);
            accountService.authorizeAccess(account, authentication.getName(), role);
            return transferService
                    .historyForAccount(accountNumber, pageable)
                    .map(TransferDtos.TransactionResponse::from);
        }

        List<String> visible =
                (role == Role.CUSTOMER
                                ? accountService.findByOwner(authentication.getName())
                                : accountService.findAll())
                        .stream().map(Account::getAccountNumber).toList();

        return transferService
                .historyForAccounts(visible, pageable)
                .map(TransferDtos.TransactionResponse::from);
    }
}
