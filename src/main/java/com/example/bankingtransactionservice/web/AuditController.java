package com.example.bankingtransactionservice.web;

import com.example.bankingtransactionservice.dto.AuditDtos;
import com.example.bankingtransactionservice.service.AuditService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Audit trail endpoint. Restricted to administrators. */
@RestController
@RequestMapping("/api/audit")
@Tag(name = "Audit", description = "Read the append-only audit trail")
@SecurityRequirement(name = "bearerAuth")
public class AuditController {

    private static final int MAX_PAGE_SIZE = 200;

    private final AuditService auditService;

    public AuditController(AuditService auditService) {
        this.auditService = auditService;
    }

    /** Returns audit events, most recent first, optionally filtered by actor. */
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
            summary = "List audit events",
            description = "Administrators only. The audit trail is append-only and cannot be modified.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Audit events returned"),
        @ApiResponse(responseCode = "403", description = "Caller is not an administrator")
    })
    public Page<AuditDtos.AuditEventResponse> list(
            @RequestParam(required = false) String actor,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE));

        if (actor != null && !actor.isBlank()) {
            return auditService.findByActor(actor, pageable).map(AuditDtos.AuditEventResponse::from);
        }
        return auditService.findAll(pageable).map(AuditDtos.AuditEventResponse::from);
    }
}
