package com.example.bankingtransactionservice.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * An append-only audit trail entry.
 *
 * <p>Written for every state-changing operation. There is deliberately no update or delete path:
 * an audit log that can be edited is not an audit log.
 */
@Entity
@Table(
        name = "audit_event",
        indexes = {
            @Index(name = "idx_audit_actor", columnList = "actor"),
            @Index(name = "idx_audit_created", columnList = "created_at")
        })
public class AuditEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 64)
    private String actor;

    @Column(nullable = false, length = 64)
    private String action;

    @Column(name = "resource_type", nullable = false, length = 64)
    private String resourceType;

    @Column(name = "resource_id", length = 64)
    private String resourceId;

    @Column(nullable = false, length = 16)
    private String outcome;

    @Lob
    @Column(name = "detail")
    private String detail;

    @Column(name = "source_ip", length = 45)
    private String sourceIp;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected AuditEvent() {
        // JPA
    }

    public AuditEvent(
            String actor,
            String action,
            String resourceType,
            String resourceId,
            String outcome,
            String detail,
            String sourceIp) {
        this.actor = actor;
        this.action = action;
        this.resourceType = resourceType;
        this.resourceId = resourceId;
        this.outcome = outcome;
        this.detail = detail;
        this.sourceIp = sourceIp;
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getActor() {
        return actor;
    }

    public String getAction() {
        return action;
    }

    public String getResourceType() {
        return resourceType;
    }

    public String getResourceId() {
        return resourceId;
    }

    public String getOutcome() {
        return outcome;
    }

    public String getDetail() {
        return detail;
    }

    public String getSourceIp() {
        return sourceIp;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
