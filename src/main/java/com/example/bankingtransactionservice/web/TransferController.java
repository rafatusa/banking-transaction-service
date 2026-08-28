package com.example.bankingtransactionservice.web;

import com.example.bankingtransactionservice.dto.TransferDtos;
import com.example.bankingtransactionservice.entity.TransactionRecord;
import com.example.bankingtransactionservice.service.TransferService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Money transfer endpoint. */
@RestController
@RequestMapping("/api/transfers")
@Tag(name = "Transfers", description = "Move money between accounts")
@SecurityRequirement(name = "bearerAuth")
public class TransferController extends ApiControllerSupport {

    private final TransferService transferService;

    public TransferController(TransferService transferService) {
        this.transferService = transferService;
    }

    /** Executes a transfer between two accounts. */
    @PostMapping
    @Operation(
            summary = "Transfer money",
            description =
                    "Debits the source account and credits the target atomically. Customers may only "
                            + "transfer from accounts they own; tellers and admins may transfer from any "
                            + "account. Rejected transfers are recorded in the audit trail.")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Transfer completed"),
        @ApiResponse(responseCode = "400", description = "Validation or business rule failure"),
        @ApiResponse(responseCode = "403", description = "Caller does not own the source account"),
        @ApiResponse(responseCode = "404", description = "An account does not exist")
    })
    public ResponseEntity<TransferDtos.TransactionResponse> transfer(
            @Valid @RequestBody TransferDtos.TransferRequest request,
            Authentication authentication,
            HttpServletRequest httpRequest) {

        TransferService.TransferCommand command =
                new TransferService.TransferCommand(
                        request.sourceAccount(),
                        request.targetAccount(),
                        request.amount(),
                        request.description(),
                        authentication.getName(),
                        currentRole(authentication),
                        clientIp(httpRequest));

        TransactionRecord record = transferService.transfer(command);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(TransferDtos.TransactionResponse.from(record));
    }
}
