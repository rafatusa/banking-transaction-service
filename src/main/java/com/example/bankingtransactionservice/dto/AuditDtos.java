package com.example.bankingtransactionservice.dto;

import com.example.bankingtransactionservice.entity.AuditEvent;
import java.time.Instant;

/** Response payloads for the audit trail. */
public final class AuditDtos {

    private AuditDtos() {
        // Container for nested record types.
    }

    /** A single audit trail entry. */
    public record AuditEventResponse(
            Long id,
            String actor,
            String action,
            String resourceType,
            String resourceId,
            String outcome,
            String detail,
            String sourceIp,
            Instant createdAt) {

        /** Maps the persistent entity onto its API representation. */
        public static AuditEventResponse from(AuditEvent event) {
            return new AuditEventResponse(
                    event.getId(),
                    event.getActor(),
                    event.getAction(),
                    event.getResourceType(),
                    event.getResourceId(),
                    event.getOutcome(),
                    event.getDetail(),
                    event.getSourceIp(),
                    event.getCreatedAt());
        }
    }
}
