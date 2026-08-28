package com.example.bankingtransactionservice.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
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

    /**
     * Free-form context for the audited action.
     *
     * <p>Deliberately NOT annotated {@code @Lob}. On PostgreSQL Hibernate maps a {@code @Lob
     * String} to {@code oid} — a pointer into {@code pg_largeobject} — while the Flyway migration
     * declares this column as {@code TEXT}. Under {@code ddl-auto=validate} that mismatch aborts
     * startup:
     *
     * <pre>
     * Schema-validation: wrong column type encountered in column [detail]
     * in table [audit_event]; found [text (Types#VARCHAR)], but expecting [oid (Types#CLOB)]
     * </pre>
     *
     * <p>{@code TEXT} is the right type here and the annotation is what was wrong. PostgreSQL
     * {@code TEXT} is already unbounded, so a large object buys nothing, and {@code oid} values
     * require explicit lifecycle management — deleting the owning row leaves the large object
     * orphaned in {@code pg_largeobject} unless it is unlinked separately. Mapping a plain String
     * to {@code text} keeps the audit trail self-contained.
     */
    @Column(name = "detail", columnDefinition = "text")
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
